/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */
package io.github.malonetalk.service.semantic;

import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.convertor.PromptConverter;
import io.github.malonetalk.dto.prompt.ColumnPromptResponse;
import io.github.malonetalk.dto.prompt.TablePromptResponse;
import io.github.malonetalk.dto.prompt.TableRelationPromptResponse;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.enums.LogicalTableRelationType;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticMergeService {

    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final LogicalTableRelationHelper logicalTableRelationHelper;

    public List<TablePromptResponse> listVisibleTablesByDomains(
            Datasource datasource, List<String> domains) {
        List<String> normalizedDomains = normalizeDomains(domains);
        // 关系和列按 table_id 对齐；表名只用于最终提示词。
        List<TableInfo> tables = tableInfoMapper.selectByDatasourceId(datasource.getId());
        Map<Integer, TableInfo> tablesById = new HashMap<>();
        for (TableInfo table : tables) {
            tablesById.put(table.getId(), table);
        }
        Map<Integer, Set<String>> availableColumnsByTableId = new HashMap<>();
        for (ColumnInfo column :
                columnSemanticInfoMapper.selectByDatasourceId(datasource.getId())) {
            if (SemanticAvailabilityHelper.isColumnAvailable(column)) {
                availableColumnsByTableId
                        .computeIfAbsent(column.getTableId(), id -> new HashSet<>())
                        .add(
                                SemanticUtils.normalizeObjectName(
                                        column.getColumnName(), "Missing columnName."));
            }
        }
        Map<Integer, List<LogicalTableRelation>> relationsBySourceId =
                logicalTableRelationMapper.selectByDatasourceId(datasource.getId()).stream()
                        .collect(Collectors.groupingBy(LogicalTableRelation::getSourceTableId));

        return tables.stream()
                .filter(
                        table ->
                                domainMatches(
                                        SemanticUtils.normalizeDomain(table.getDomain()),
                                        normalizedDomains))
                .map(
                        table ->
                                PromptConverter.mapTablePrompt(
                                        table,
                                        filterVisibleLogicalRelations(
                                                relationsBySourceId.getOrDefault(
                                                        table.getId(), List.of()),
                                                tablesById,
                                                availableColumnsByTableId)))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ColumnPromptResponse> getTableSchema(Datasource datasource, String tableName) {
        String normalizedTableName =
                SemanticUtils.normalizeObjectName(
                        tableName, "Missing tableName for merged table schema lookup.");

        TableInfo semanticTable =
                tableInfoMapper.selectByDatasourceIdAndTableName(
                        datasource.getId(), normalizedTableName);
        if (semanticTable == null || !SemanticAvailabilityHelper.hasPhysicalTable(semanticTable)) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "The physical table does not exist or is unavailable. Synchronize the table"
                            + " schema and try again.");
        }
        if (!Boolean.TRUE.equals(semanticTable.getIsVisible())) {
            throw BusinessException.of(ErrorCode.TABLE_HIDDEN);
        }

        List<ColumnInfo> columns =
                columnSemanticInfoMapper.selectByDatasourceIdAndTableName(
                        datasource.getId(), normalizedTableName);
        if (columns.isEmpty()) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "No synced columns found for table "
                            + normalizedTableName
                            + ". Synchronize the table schema and try again.");
        }

        return columns.stream()
                .map(PromptConverter::mapColumnPrompt)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<TableRelationPromptResponse> filterVisibleLogicalRelations(
            List<LogicalTableRelation> logicalRelations,
            Map<Integer, TableInfo> tablesById,
            Map<Integer, Set<String>> availableColumnsByTableId) {
        List<TableRelationPromptResponse> visibleRelations = new ArrayList<>();
        for (LogicalTableRelation relation : logicalRelations) {
            if (!Boolean.TRUE.equals(relation.getIsEnabled())) {
                continue;
            }
            if (isUnavailableTable(tablesById.get(relation.getSourceTableId()))
                    || isUnavailableTable(tablesById.get(relation.getTargetTableId()))) {
                continue;
            }
            List<String> sourceColumns = parseRelationColumns(relation, true);
            List<String> targetColumns = parseRelationColumns(relation, false);
            if (sourceColumns == null || targetColumns == null) {
                continue;
            }
            if (hasUnavailableColumn(
                            availableColumnsByTableId, relation.getSourceTableId(), sourceColumns)
                    || hasUnavailableColumn(
                            availableColumnsByTableId,
                            relation.getTargetTableId(),
                            targetColumns)) {
                continue;
            }
            visibleRelations.add(
                    new TableRelationPromptResponse(
                            LogicalTableRelationType.fromCode(relation.getRelationType()),
                            SemanticConstants.RELATION_SOURCE_LOGICAL,
                            relation.getSourceTableName(),
                            sourceColumns,
                            relation.getTargetTableName(),
                            targetColumns,
                            relation.getDescription()));
        }
        return visibleRelations;
    }

    private boolean hasUnavailableColumn(
            Map<Integer, Set<String>> availableColumnsByTableId,
            Integer tableId,
            List<String> columnNames) {
        Set<String> availableColumns = availableColumnsByTableId.getOrDefault(tableId, Set.of());
        return columnNames.stream()
                .map(name -> SemanticUtils.normalizeObjectName(name, "Missing columnName."))
                .anyMatch(name -> !availableColumns.contains(name));
    }

    private boolean isUnavailableTable(TableInfo table) {
        return table == null || !SemanticAvailabilityHelper.isTableAvailable(table);
    }

    private List<String> parseRelationColumns(LogicalTableRelation relation, boolean source) {
        String fieldName = source ? "sourceColumnNames" : "targetColumnNames";
        String json =
                source ? relation.getSourceColumnNamesJson() : relation.getTargetColumnNamesJson();
        try {
            return logicalTableRelationHelper.fromJson(json, fieldName);
        } catch (BusinessException e) {
            log.warn(
                    "Skip relation id={}: invalid {} - {}",
                    relation.getId(),
                    fieldName,
                    e.getMessage());
            return null;
        }
    }

    private List<String> normalizeDomains(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        return domains.stream()
                .map(SemanticUtils::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean domainMatches(String domain, List<String> domains) {
        if (domains.isEmpty()) {
            return true;
        }
        return domains.stream().anyMatch(d -> d.equalsIgnoreCase(domain));
    }
}

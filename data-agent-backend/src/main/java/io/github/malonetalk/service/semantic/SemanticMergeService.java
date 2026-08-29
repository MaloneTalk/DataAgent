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

import io.github.malonetalk.common.ErrorCode;
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
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.semantic.enums.UsageLevelEnum;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        TableNameIndex tableIndex =
                TableNameIndex.of(tableInfoMapper.selectByDatasourceId(datasource.getId()));
        TableColumnIndex columnIndex =
                TableColumnIndex.of(
                        columnSemanticInfoMapper.selectByDatasourceId(datasource.getId()));
        RelationSourceIndex relationIndex =
                RelationSourceIndex.of(
                        logicalTableRelationMapper.selectByDatasourceId(datasource.getId()));

        return tableIndex.asList().stream()
                .map(
                        table ->
                                PromptConverter.mapTablePrompt(
                                        table,
                                        resolveVisibleRelations(
                                                table.getTableName(),
                                                tableIndex,
                                                columnIndex,
                                                relationIndex)))
                .filter(Objects::nonNull)
                .filter(table -> domainMatches(table.domain(), normalizedDomains))
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

    private List<TableRelationPromptResponse> resolveVisibleRelations(
            String sourceTableName,
            TableNameIndex tableIndex,
            TableColumnIndex columnIndex,
            RelationSourceIndex relationIndex) {
        List<ResolvedLogicalRelation> visibleRelations =
                filterVisibleLogicalRelations(
                        relationIndex.get(sourceTableName), tableIndex, columnIndex);
        return deduplicateRelations(visibleRelations);
    }

    private List<ResolvedLogicalRelation> filterVisibleLogicalRelations(
            List<LogicalTableRelation> logicalRelations,
            TableNameIndex tableIndex,
            TableColumnIndex columnIndex) {
        List<ResolvedLogicalRelation> visibleRelations = new ArrayList<>();
        for (LogicalTableRelation relation : logicalRelations) {
            if (!Boolean.TRUE.equals(relation.getIsEnabled())) {
                continue;
            }
            if (tableIndex.isUnavailable(relation.getSourceTableName())
                    || tableIndex.isUnavailable(relation.getTargetTableName())) {
                continue;
            }
            List<String> sourceColumns = parseRelationColumns(relation, true);
            List<String> targetColumns = parseRelationColumns(relation, false);
            if (sourceColumns == null || targetColumns == null) {
                continue;
            }
            if (columnIndex.hasUnavailableColumn(relation.getSourceTableName(), sourceColumns)
                    || columnIndex.hasUnavailableColumn(
                            relation.getTargetTableName(), targetColumns)) {
                continue;
            }
            visibleRelations.add(
                    new ResolvedLogicalRelation(relation, sourceColumns, targetColumns));
        }
        return visibleRelations;
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

    private List<TableRelationPromptResponse> deduplicateRelations(
            List<ResolvedLogicalRelation> relations) {
        LinkedHashMap<String, TableRelationPromptResponse> merged = new LinkedHashMap<>();
        for (ResolvedLogicalRelation relation : relations) {
            String key =
                    logicalTableRelationHelper.buildRelationKey(
                            relation.sourceTableName(),
                            relation.sourceColumns(),
                            relation.targetTableName(),
                            relation.targetColumns());
            merged.put(key, toPromptResponse(relation));
        }
        return List.copyOf(merged.values());
    }

    private TableRelationPromptResponse toPromptResponse(ResolvedLogicalRelation relation) {
        return new TableRelationPromptResponse(
                LogicalTableRelationType.fromCode(relation.relation().getRelationType()),
                SemanticConstants.RELATION_SOURCE_LOGICAL,
                relation.sourceTableName(),
                relation.sourceColumns(),
                relation.targetTableName(),
                relation.targetColumns(),
                relation.relation().getDescription());
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

    private record ResolvedLogicalRelation(
            LogicalTableRelation relation, List<String> sourceColumns, List<String> targetColumns) {

        private String sourceTableName() {
            return relation.getSourceTableName();
        }

        private String targetTableName() {
            return relation.getTargetTableName();
        }
    }

    private record TableNameIndex(Map<String, TableInfo> index) {

        private static TableNameIndex of(List<TableInfo> tables) {
            Map<String, TableInfo> map = new LinkedHashMap<>();
            for (TableInfo table : tables) {
                map.put(
                        SemanticUtils.normalizeObjectName(
                                table.getTableName(),
                                "Missing tableName while building semantic table index."),
                        table);
            }
            return new TableNameIndex(map);
        }

        private List<TableInfo> asList() {
            return List.copyOf(index.values());
        }

        private TableInfo get(String tableName) {
            return index.get(
                    SemanticUtils.normalizeObjectName(
                            tableName, "Missing tableName while reading semantic table index."));
        }

        private boolean isUnavailable(String tableName) {
            TableInfo tableInfo = get(tableName);
            return tableInfo == null
                    || !SemanticAvailabilityHelper.isTableAvailable(
                            tableInfo, UsageLevelEnum.AI_PROMPT);
        }
    }

    private record TableColumnIndex(Map<String, Map<String, ColumnInfo>> index) {

        private static TableColumnIndex of(List<ColumnInfo> columns) {
            Map<String, Map<String, ColumnInfo>> map = new HashMap<>();
            for (ColumnInfo column : columns) {
                map.computeIfAbsent(
                                SemanticUtils.normalizeObjectName(
                                        column.getTableName(),
                                        "Missing tableName while building semantic column index."),
                                key -> new HashMap<>())
                        .put(
                                SemanticUtils.normalizeObjectName(
                                        column.getColumnName(),
                                        "Missing columnName while building semantic column index."),
                                column);
            }
            return new TableColumnIndex(map);
        }

        private ColumnInfo get(String tableName, String columnName) {
            Map<String, ColumnInfo> columns =
                    index.get(
                            SemanticUtils.normalizeObjectName(
                                    tableName,
                                    "Missing tableName while reading semantic column index."));
            return columns == null
                    ? null
                    : columns.get(
                            SemanticUtils.normalizeObjectName(
                                    columnName,
                                    "Missing columnName while reading semantic column index."));
        }

        private boolean hasUnavailableColumn(String tableName, List<String> columnNames) {
            for (String columnName : columnNames) {
                ColumnInfo columnInfo = get(tableName, columnName);
                if (columnInfo == null
                        || !SemanticAvailabilityHelper.isColumnAvailable(
                                columnInfo, UsageLevelEnum.AI_PROMPT)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record RelationSourceIndex(Map<String, List<LogicalTableRelation>> index) {

        private static RelationSourceIndex of(List<LogicalTableRelation> relations) {
            Map<String, List<LogicalTableRelation>> map = new HashMap<>();
            for (LogicalTableRelation relation : relations) {
                map.computeIfAbsent(
                                SemanticUtils.normalizeObjectName(
                                        relation.getSourceTableName(),
                                        "Missing sourceTableName while building relation index."),
                                key -> new ArrayList<>())
                        .add(relation);
            }
            return new RelationSourceIndex(map);
        }

        private List<LogicalTableRelation> get(String sourceTableName) {
            return index.getOrDefault(
                    SemanticUtils.normalizeObjectName(
                            sourceTableName,
                            "Missing sourceTableName while reading relation index."),
                    Collections.emptyList());
        }
    }
}

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

import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.convertor.PromptConverter;
import io.github.malonetalk.dto.prompt.ColumnPromptResponse;
import io.github.malonetalk.dto.prompt.TablePromptResponse;
import io.github.malonetalk.dto.prompt.TableRelationPromptResponse;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticMergeService {

    private final SchemaReader schemaReader;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final LogicalTableRelationHelper logicalTableRelationHelper;

    public List<TablePromptResponse> listVisibleTablesByDomains(
            Datasource datasource, List<String> domains) {
        List<String> normalizedDomains = normalizeDomains(domains);
        Map<String, TableInfo> semanticByKey = buildSemanticTablesByKey(datasource.getId());
        Map<String, Map<String, io.github.malonetalk.entity.ColumnInfo>> semanticColumnsByTable =
                buildSemanticColumnsByTable(datasource.getId());
        Map<String, List<LogicalTableRelation>> logicalRelationsBySource =
                buildLogicalRelationsBySource(datasource.getId());

        return schemaReader.getTables(datasource).stream()
                .map(
                        physical ->
                                PromptConverter.mapTablePrompt(
                                        physical,
                                        semanticByKey,
                                        resolveVisibleRelations(
                                                physical.tableName(),
                                                semanticByKey,
                                                semanticColumnsByTable,
                                                logicalRelationsBySource)))
                .filter(java.util.Objects::nonNull)
                .filter(table -> domainMatches(table.domain(), normalizedDomains))
                .toList();
    }

    public List<ColumnPromptResponse> getTableSchema(Datasource datasource, String tableName) {
        String normalizedTableName = SemanticUtils.requireName(tableName, "tableName");

        List<io.github.malonetalk.dto.datasource.ColumnInfo> physicalColumns =
                schemaReader.getTableSchema(datasource, normalizedTableName);
        if (physicalColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Table " + normalizedTableName + " does not exist or has no readable columns.");
        }

        TableInfo semanticTable =
                tableInfoMapper.selectByDatasourceIdAndTableName(
                        datasource.getId(), normalizedTableName);
        if (semanticTable != null && !Boolean.TRUE.equals(semanticTable.getIsVisible())) {
            throw new IllegalArgumentException("Table " + normalizedTableName + " is hidden.");
        }

        Map<String, io.github.malonetalk.entity.ColumnInfo> semanticByKey =
                buildSemanticColumnsByKey(datasource.getId(), normalizedTableName);

        return physicalColumns.stream()
                .map(physical -> PromptConverter.mapColumnPrompt(physical, semanticByKey))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<TableRelationPromptResponse> resolveVisibleRelations(
            String sourceTableName,
            Map<String, TableInfo> semanticByKey,
            Map<String, Map<String, io.github.malonetalk.entity.ColumnInfo>> semanticColumnsByTable,
            Map<String, List<LogicalTableRelation>> logicalRelationsBySource) {
        List<LogicalTableRelation> logicalRelations =
                logicalRelationsBySource.getOrDefault(
                        sourceTableName.toLowerCase(Locale.ROOT), Collections.emptyList());
        List<ResolvedLogicalRelation> visibleRelations =
                filterVisibleLogicalRelations(
                        logicalRelations, semanticByKey, semanticColumnsByTable);
        return deduplicateRelations(visibleRelations);
    }

    private List<ResolvedLogicalRelation> filterVisibleLogicalRelations(
            List<LogicalTableRelation> logicalRelations,
            Map<String, TableInfo> semanticByKey,
            Map<String, Map<String, io.github.malonetalk.entity.ColumnInfo>>
                    semanticColumnsByTable) {
        List<ResolvedLogicalRelation> visibleRelations = new ArrayList<>();
        for (LogicalTableRelation relation : logicalRelations) {
            if (!Boolean.TRUE.equals(relation.getIsEnabled())) {
                continue;
            }
            if (tableHidden(relation.getSourceTableName(), semanticByKey)
                    || tableHidden(relation.getTargetTableName(), semanticByKey)) {
                continue;
            }
            List<String> sourceColumns;
            List<String> targetColumns;
            try {
                sourceColumns =
                        logicalTableRelationHelper.fromJson(
                                relation.getSourceColumnNamesJson(), "sourceColumnNames");
                targetColumns =
                        logicalTableRelationHelper.fromJson(
                                relation.getTargetColumnNamesJson(), "targetColumnNames");
            } catch (IllegalArgumentException e) {
                log.warn(
                        "Skip invalid logical relation id={}: {}",
                        relation.getId(),
                        e.getMessage());
                continue;
            }
            if (columnsHidden(relation.getSourceTableName(), sourceColumns, semanticColumnsByTable)
                    || columnsHidden(
                            relation.getTargetTableName(), targetColumns, semanticColumnsByTable)) {
                continue;
            }
            visibleRelations.add(
                    new ResolvedLogicalRelation(relation, sourceColumns, targetColumns));
        }
        return visibleRelations;
    }

    private List<TableRelationPromptResponse> deduplicateRelations(
            List<ResolvedLogicalRelation> relations) {
        LinkedHashMap<String, TableRelationPromptResponse> merged = new LinkedHashMap<>();
        for (ResolvedLogicalRelation relation : relations) {
            String key =
                    buildRelationMergeKey(
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
                relation.relation().getRelationType(),
                LogicalTableRelationHelper.RELATION_SOURCE_LOGICAL,
                relation.sourceTableName(),
                relation.sourceColumns(),
                relation.targetTableName(),
                relation.targetColumns(),
                relation.relation().getDescription());
    }

    private String buildRelationMergeKey(
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns) {
        return logicalTableRelationHelper.buildRelationKey(
                sourceTable, sourceColumns, targetTable, targetColumns);
    }

    private Map<String, io.github.malonetalk.entity.ColumnInfo> buildSemanticColumnsByKey(
            Integer datasourceId, String tableName) {
        Map<String, io.github.malonetalk.entity.ColumnInfo> result = new HashMap<>();
        for (io.github.malonetalk.entity.ColumnInfo column :
                columnSemanticInfoMapper.selectByDatasourceIdAndTableName(
                        datasourceId, tableName)) {
            result.put(column.getColumnName().toLowerCase(Locale.ROOT), column);
        }
        return result;
    }

    private Map<String, Map<String, io.github.malonetalk.entity.ColumnInfo>>
            buildSemanticColumnsByTable(Integer datasourceId) {
        Map<String, Map<String, io.github.malonetalk.entity.ColumnInfo>> result = new HashMap<>();
        for (io.github.malonetalk.entity.ColumnInfo column :
                columnSemanticInfoMapper.selectByDatasourceId(datasourceId)) {
            result.computeIfAbsent(
                            column.getTableName().toLowerCase(Locale.ROOT), key -> new HashMap<>())
                    .put(column.getColumnName().toLowerCase(Locale.ROOT), column);
        }
        return result;
    }

    private Map<String, TableInfo> buildSemanticTablesByKey(Integer datasourceId) {
        Map<String, TableInfo> result = new HashMap<>();
        for (TableInfo table : tableInfoMapper.selectByDatasourceId(datasourceId)) {
            result.put(table.getTableName().toLowerCase(Locale.ROOT), table);
        }
        return result;
    }

    private Map<String, List<LogicalTableRelation>> buildLogicalRelationsBySource(
            Integer datasourceId) {
        List<LogicalTableRelation> allRelations =
                logicalTableRelationMapper.selectByDatasourceId(datasourceId);
        Map<String, List<LogicalTableRelation>> result = new HashMap<>();
        for (LogicalTableRelation relation : allRelations) {
            String key = relation.getSourceTableName().toLowerCase(Locale.ROOT);
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(relation);
        }
        return result;
    }

    private List<String> normalizeDomains(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        return domains.stream()
                .map(SemanticUtils::normalizeBlankToNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean domainMatches(String domain, List<String> domains) {
        if (domains.isEmpty()) {
            return true;
        }
        return domains.stream().anyMatch(d -> d.equalsIgnoreCase(domain));
    }

    private boolean tableHidden(String tableName, Map<String, TableInfo> semanticByKey) {
        TableInfo tableInfo = semanticByKey.get(tableName.toLowerCase(Locale.ROOT));
        return tableInfo != null && !Boolean.TRUE.equals(tableInfo.getIsVisible());
    }

    private boolean columnsHidden(
            String tableName,
            List<String> columnNames,
            Map<String, Map<String, io.github.malonetalk.entity.ColumnInfo>>
                    semanticColumnsByTable) {
        Map<String, io.github.malonetalk.entity.ColumnInfo> semanticColumns =
                semanticColumnsByTable.getOrDefault(
                        tableName.toLowerCase(Locale.ROOT), Collections.emptyMap());
        for (String columnName : columnNames) {
            io.github.malonetalk.entity.ColumnInfo columnInfo =
                    semanticColumns.get(columnName.toLowerCase(Locale.ROOT));
            if (columnInfo != null && !Boolean.TRUE.equals(columnInfo.getIsVisible())) {
                return true;
            }
        }
        return false;
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
}

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
import io.github.malonetalk.entity.ColumnInfo;
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
        TableNameIndex tableIndex =
                TableNameIndex.of(tableInfoMapper.selectByDatasourceId(datasource.getId()));
        TableColumnIndex columnIndex =
                TableColumnIndex.of(
                        columnSemanticInfoMapper.selectByDatasourceId(datasource.getId()));
        RelationSourceIndex relationIndex =
                RelationSourceIndex.of(
                        logicalTableRelationMapper.selectByDatasourceId(datasource.getId()));

        return schemaReader.getTables(datasource).stream()
                .map(
                        physical ->
                                PromptConverter.mapTablePrompt(
                                        physical,
                                        tableIndex.asMap(),
                                        resolveVisibleRelations(
                                                physical.tableName(),
                                                tableIndex,
                                                columnIndex,
                                                relationIndex)))
                .filter(java.util.Objects::nonNull)
                .filter(table -> domainMatches(table.domain(), normalizedDomains))
                .toList();
    }

    public List<ColumnPromptResponse> getTableSchema(Datasource datasource, String tableName) {
        String normalizedTableName = SemanticUtils.checkNotBlank(tableName, "tableName").trim();

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

        Map<String, ColumnInfo> semanticColumnIndex =
                buildSemanticColumnIndex(datasource.getId(), normalizedTableName);

        return physicalColumns.stream()
                .map(physical -> PromptConverter.mapColumnPrompt(physical, semanticColumnIndex))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<TableRelationPromptResponse> resolveVisibleRelations(
            String sourceTableName,
            TableNameIndex tableIndex,
            TableColumnIndex columnIndex,
            RelationSourceIndex relationIndex) {
        List<LogicalTableRelation> logicalRelations = relationIndex.get(sourceTableName);
        List<ResolvedLogicalRelation> visibleRelations =
                filterVisibleLogicalRelations(logicalRelations, tableIndex, columnIndex);
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
            if (tableIndex.isHidden(relation.getSourceTableName())
                    || tableIndex.isHidden(relation.getTargetTableName())) {
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
            if (columnIndex.hasHiddenColumn(relation.getSourceTableName(), sourceColumns)
                    || columnIndex.hasHiddenColumn(relation.getTargetTableName(), targetColumns)) {
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
                logicalTableRelationHelper.relationType(relation.relation().getRelationType()),
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

    private Map<String, ColumnInfo> buildSemanticColumnIndex(
            Integer datasourceId, String tableName) {
        Map<String, ColumnInfo> result = new HashMap<>();
        for (ColumnInfo column :
                columnSemanticInfoMapper.selectByDatasourceIdAndTableName(
                        datasourceId, tableName)) {
            result.put(column.getColumnName().toLowerCase(Locale.ROOT), column);
        }
        return result;
    }

    private List<String> normalizeDomains(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        return domains.stream()
                .map(SemanticUtils::trimToNull)
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
            Map<String, TableInfo> map = new HashMap<>();
            for (TableInfo table : tables) {
                map.put(table.getTableName().toLowerCase(Locale.ROOT), table);
            }
            return new TableNameIndex(map);
        }

        private Map<String, TableInfo> asMap() {
            return index;
        }

        private TableInfo get(String tableName) {
            return index.get(tableName.toLowerCase(Locale.ROOT));
        }

        private boolean isHidden(String tableName) {
            TableInfo tableInfo = get(tableName);
            return tableInfo != null && !Boolean.TRUE.equals(tableInfo.getIsVisible());
        }
    }

    private record TableColumnIndex(Map<String, Map<String, ColumnInfo>> index) {

        private static TableColumnIndex of(List<ColumnInfo> columns) {
            Map<String, Map<String, ColumnInfo>> map = new HashMap<>();
            for (ColumnInfo column : columns) {
                map.computeIfAbsent(
                                column.getTableName().toLowerCase(Locale.ROOT),
                                key -> new HashMap<>())
                        .put(column.getColumnName().toLowerCase(Locale.ROOT), column);
            }
            return new TableColumnIndex(map);
        }

        private ColumnInfo get(String tableName, String columnName) {
            Map<String, ColumnInfo> columns = index.get(tableName.toLowerCase(Locale.ROOT));
            return columns == null ? null : columns.get(columnName.toLowerCase(Locale.ROOT));
        }

        private boolean hasHiddenColumn(String tableName, List<String> columnNames) {
            for (String columnName : columnNames) {
                ColumnInfo columnInfo = get(tableName, columnName);
                if (columnInfo != null && !Boolean.TRUE.equals(columnInfo.getIsVisible())) {
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
                                relation.getSourceTableName().toLowerCase(Locale.ROOT),
                                key -> new ArrayList<>())
                        .add(relation);
            }
            return new RelationSourceIndex(map);
        }

        private List<LogicalTableRelation> get(String sourceTableName) {
            return index.getOrDefault(
                    sourceTableName.toLowerCase(Locale.ROOT), Collections.emptyList());
        }
    }
}

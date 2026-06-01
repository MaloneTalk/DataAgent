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

import io.github.malonetalk.agent.datasource.ColumnInfo;
import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.agent.tools.response.ColumnPromptResponse;
import io.github.malonetalk.agent.tools.response.TablePromptResponse;
import io.github.malonetalk.agent.tools.response.TableRelationResponse;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.TableSchemaSemanticPrompt;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticMergeService {

    private final DatasourceService datasourceService;
    private final SchemaReader schemaReader;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final LogicalTableRelationHelper logicalTableRelationHelper;

    public PageResponse<TablePromptResponse> getVisibleTablePromptPage(int page, int pageSize) {
        Datasource datasource = datasourceService.requireActiveDatasource();
        List<TableInfo> physicalTables = schemaReader.getTables(datasource);
        List<TableInfo> semanticTables =
                tableInfoMapper.selectByDatasourceId(datasource.getId());
        Map<String, TableInfo> semanticByKey =
                toTableKeyMap(semanticTables);

        List<TablePromptResponse> visibleTables = new ArrayList<>();
        for (TableInfo physicalTable : physicalTables) {
            String key = normalizeKey(physicalTable.getTableName());
            TableInfo semanticTable = semanticByKey.get(key);
            if (!isTableVisible(semanticTable)) {
                continue;
            }
            String description =
                    resolveSemanticFirst(
                            semanticTable != null ? semanticTable.getTableDescription() : null,
                            physicalTable.getTableDescription());
            String domain =
                    resolveSemanticFirst(
                            semanticTable != null ? semanticTable.getDomain() : null, null);
            visibleTables.add(
                    new TablePromptResponse(
                            physicalTable.getTableName(),
                            domain,
                            description,
                            Collections.emptyList()));
        }

        // 为每个可见表附加关系
        Map<String, List<LogicalTableRelation>> logicalRelationsBySource =
                buildLogicalRelationsBySource(datasource.getId());
        for (int i = 0; i < visibleTables.size(); i++) {
            TablePromptResponse item = visibleTables.get(i);
            List<TableRelationResponse> relations =
                    resolveVisibleRelations(
                            item.name(), logicalRelationsBySource, datasource.getId());
            visibleTables.set(
                    i,
                    new TablePromptResponse(
                            item.name(),
                            item.domain(),
                            item.description(),
                            relations));
        }

        // 内存分页
        return paginateInMemory(visibleTables, page, pageSize);
    }

    public TableSchemaSemanticPrompt getTableSchema(String tableName, int page, int pageSize) {
        Datasource datasource = datasourceService.requireActiveDatasource();
        String normalizedTableName = SemanticUtils.requireName(tableName, "tableName");

        // 校验表存在且可见
        TableInfo semanticTable =
                tableInfoMapper.selectByDatasourceIdAndTableName(
                        datasource.getId(), normalizedTableName);
        if (!isTableVisible(semanticTable)) {
            throw new IllegalArgumentException("表 " + normalizedTableName + " 不存在或不可见。");
        }

        // 读物理列
        List<ColumnInfo> physicalColumns;
        try {
            physicalColumns = schemaReader.getTableSchema(datasource, normalizedTableName);
        } catch (SchemaReader.SchemaReadException e) {
            throw new IllegalArgumentException(
                    "无法读取表 " + normalizedTableName + " 的 Schema: " + e.getMessage(), e);
        }

        // 读语义列
        if (physicalColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "表 " + normalizedTableName + " 不存在或没有可读列。");
        }

        List<io.github.malonetalk.entity.ColumnInfo> semanticColumns =
                columnSemanticInfoMapper.selectByDatasourceIdAndTableName(
                        datasource.getId(), normalizedTableName);
        Map<String, io.github.malonetalk.entity.ColumnInfo> semanticByKey =
                toColumnKeyMap(semanticColumns);

        // 合并列
        List<ColumnPromptResponse> visibleColumns = new ArrayList<>();
        for (ColumnInfo physicalColumn : physicalColumns) {
            String key = normalizeKey(physicalColumn.columnName());
            io.github.malonetalk.entity.ColumnInfo semanticColumn = semanticByKey.get(key);
            if (!isColumnVisible(semanticColumn)) {
                continue;
            }
            visibleColumns.add(mapColumnPrompt(physicalColumn, semanticColumn));
        }

        String description =
                resolveSemanticFirst(
                        semanticTable != null ? semanticTable.getTableDescription() : null, null);
        String domain =
                resolveSemanticFirst(
                        semanticTable != null ? semanticTable.getDomain() : null, null);

        PageResponse<ColumnPromptResponse> columnPage =
                paginateInMemory(visibleColumns, page, pageSize);
        return new TableSchemaSemanticPrompt(
                normalizedTableName, domain, description, columnPage);
    }

    private List<TableRelationResponse> resolveVisibleRelations(
            String sourceTableName,
            Map<String, List<LogicalTableRelation>> logicalRelationsBySource,
            Integer datasourceId) {
        List<LogicalTableRelation> logicalRelations =
                logicalRelationsBySource.getOrDefault(
                        normalizeKey(sourceTableName), Collections.emptyList());
        if (logicalRelations.isEmpty()) {
            return Collections.emptyList();
        }
        List<TableRelationResponse> result = new ArrayList<>();
        for (LogicalTableRelation relation : logicalRelations) {
            if (!Boolean.TRUE.equals(relation.getIsEnabled())) {
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
                        "跳过无效的逻辑关系 id={}: {}", relation.getId(), e.getMessage());
                continue;
            }
            result.add(
                    new TableRelationResponse(
                            relation.getRelationType(),
                            LogicalTableRelationHelper.RELATION_SOURCE_LOGICAL,
                            relation.getSourceTableName(),
                            sourceColumns,
                            relation.getTargetTableName(),
                            targetColumns,
                            relation.getDescription()));
        }
        return result;
    }

    private ColumnPromptResponse mapColumnPrompt(
            ColumnInfo physicalColumn,
            io.github.malonetalk.entity.ColumnInfo semanticColumn) {
        String description =
                resolveSemanticFirst(
                        semanticColumn != null ? semanticColumn.getColumnDescription() : null,
                        physicalColumn.remarks());
        StringBuilder typeText = new StringBuilder(physicalColumn.typeName());
        if (physicalColumn.columnSize() > 0) {
            typeText.append("(").append(physicalColumn.columnSize()).append(")");
        }
        return new ColumnPromptResponse(
                physicalColumn.columnName(),
                typeText.toString(),
                physicalColumn.primaryKey(),
                physicalColumn.nullable(),
                SemanticUtils.normalizeBlankToNull(physicalColumn.defaultValue()),
                description);
    }

    private boolean isTableVisible(TableInfo semanticTable) {
        if (semanticTable != null) {
            return Boolean.TRUE.equals(semanticTable.getIsVisible());
        }
        return true;
    }

    private boolean isColumnVisible(io.github.malonetalk.entity.ColumnInfo semanticColumn) {
        if (semanticColumn != null) {
            return Boolean.TRUE.equals(semanticColumn.getIsVisible());
        }
        return true;
    }

    private Map<String, List<LogicalTableRelation>> buildLogicalRelationsBySource(
            Integer datasourceId) {
        List<LogicalTableRelation> allRelations =
                logicalTableRelationMapper.selectByDatasourceId(datasourceId);
        Map<String, List<LogicalTableRelation>> result = new HashMap<>();
        for (LogicalTableRelation relation : allRelations) {
            String key = normalizeKey(relation.getSourceTableName());
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(relation);
        }
        return result;
    }

    private Map<String, TableInfo> toTableKeyMap(List<TableInfo> tables) {
        Map<String, TableInfo> map = new LinkedHashMap<>();
        for (TableInfo table : tables) {
            map.put(normalizeKey(table.getTableName()), table);
        }
        return map;
    }

    private Map<String, io.github.malonetalk.entity.ColumnInfo> toColumnKeyMap(
            List<io.github.malonetalk.entity.ColumnInfo> columns) {
        Map<String, io.github.malonetalk.entity.ColumnInfo> map = new LinkedHashMap<>();
        for (io.github.malonetalk.entity.ColumnInfo column : columns) {
            map.put(normalizeKey(column.getColumnName()), column);
        }
        return map;
    }

    private <T> PageResponse<T> paginateInMemory(
            List<T> items, int page, int pageSize) {
        if (items.isEmpty()) {
            return PageResponse.empty(page, pageSize);
        }
        int start = (page - 1) * pageSize;
        if (start >= items.size()) {
            return PageResponse.empty(page, pageSize);
        }
        int end = Math.min(start + pageSize, items.size());
        List<T> pageItems = items.subList(start, end);
        return PageResponse.of(pageItems, items.size(), page, pageSize);
    }

    private String normalizeKey(String value) {
        return logicalTableRelationHelper.normalizeIdentifierKey(value);
    }

    private String resolveSemanticFirst(String semanticValue, String physicalValue) {
        String resolved = SemanticUtils.normalizeBlankToNull(semanticValue);
        if (resolved != null) {
            return resolved;
        }
        return SemanticUtils.normalizeBlankToNull(physicalValue);
    }
}

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
package io.github.malonetalk.service.semantic.sync;

import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.dto.semantic.SyncTableResult;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SemanticSyncApplyService {

    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;

    @Transactional
    public List<SyncTableResult> applyTableSync(
            Integer datasourceId, List<TableSyncSource> presentTables, List<String> missingTables) {
        Set<String> tableNames = new LinkedHashSet<>();
        presentTables.forEach(table -> tableNames.add(table.tableName()));
        tableNames.addAll(missingTables);
        if (tableNames.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<String> selectedTableNames = List.copyOf(tableNames);

        Map<String, TableInfo> existingTableIndex =
                loadSemanticTableIndex(datasourceId, selectedTableNames);
        Map<String, List<ColumnInfo>> columnsByTableName =
                loadSemanticColumnsByTable(datasourceId, selectedTableNames);

        batchSavePresentSchema(datasourceId, presentTables, existingTableIndex, columnsByTableName);

        // 基于写入前快照生成统计结果，同时收集需要批量标记缺失的列 ID。
        List<String> missingTableNames = new ArrayList<>();
        List<Integer> missingColumnIds = new ArrayList<>();
        List<SyncTableResult> results = new ArrayList<>();
        for (TableSyncSource table : presentTables) {
            String tableKey = tableKey(table.tableName());
            results.add(
                    buildPresentTableResult(
                            table,
                            existingTableIndex.get(tableKey),
                            columnsByTableName.getOrDefault(tableKey, List.of()),
                            missingColumnIds));
        }
        for (String missingTable : missingTables) {
            String tableKey = tableKey(missingTable);
            SyncTableResult result =
                    buildMissingTableResult(
                            missingTable,
                            existingTableIndex.get(tableKey),
                            columnsByTableName.getOrDefault(tableKey, List.of()),
                            missingColumnIds);
            results.add(result);
            if (result.tableMarkedAsMissing()) {
                missingTableNames.add(missingTable);
            }
        }

        markMissingTables(datasourceId, missingTableNames, now);
        markMissingColumns(datasourceId, missingColumnIds, now);
        return results;
    }

    @Transactional
    public List<SyncTableResult> refreshPhysicalStatus(
            Integer datasourceId,
            Set<String> physicalTableNames,
            Map<String, Set<String>> physicalColumnNamesByTable) {
        // 全量对比语义缓存与物理结构，在内存中收集差异后统一批量更新。
        List<TableInfo> semanticTables = tableInfoMapper.selectByDatasourceId(datasourceId);
        if (semanticTables.isEmpty()) {
            return List.of();
        }
        List<String> tableNames =
                semanticTables.stream().map(TableInfo::getTableName).distinct().toList();
        Map<String, List<ColumnInfo>> columnsByTableName =
                loadSemanticColumnsByTable(datasourceId, tableNames);
        List<String> tableNamesToMarkMissing = new ArrayList<>();
        List<Integer> columnIdsToMarkMissing = new ArrayList<>();
        List<SyncTableResult> results = new ArrayList<>();

        for (TableInfo tableInfo : semanticTables) {
            String normalizedTableName =
                    SemanticUtils.normalizeObjectName(
                            tableInfo.getTableName(), "Missing semantic tableName.");
            List<ColumnInfo> columns =
                    columnsByTableName.getOrDefault(normalizedTableName, List.of());
            if (!physicalTableNames.contains(normalizedTableName)) {
                SyncTableResult result =
                        buildMissingTableResult(
                                normalizedTableName, tableInfo, columns, columnIdsToMarkMissing);
                results.add(result);
                if (result.tableMarkedAsMissing()) {
                    tableNamesToMarkMissing.add(normalizedTableName);
                }
                continue;
            }

            int missingColumnsMarked =
                    collectMissingColumnIds(
                            columns,
                            physicalColumnNamesByTable.getOrDefault(normalizedTableName, Set.of()),
                            columnIdsToMarkMissing);
            if (missingColumnsMarked > 0) {
                results.add(
                        SyncTableResult.builder()
                                .tableName(tableInfo.getTableName())
                                .physicalTableFound(true)
                                .missingColumnsMarked(missingColumnsMarked)
                                .message("Physical column status refreshed")
                                .build());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        markMissingTables(datasourceId, tableNamesToMarkMissing, now);
        markMissingColumns(datasourceId, columnIdsToMarkMissing, now);
        return results;
    }

    private void batchSavePresentSchema(
            Integer datasourceId,
            List<TableSyncSource> presentTables,
            Map<String, TableInfo> existingTableIndex,
            Map<String, List<ColumnInfo>> columnsByTableName) {
        if (presentTables.isEmpty()) {
            return;
        }

        List<TableInfo> newTables = new ArrayList<>();
        List<ColumnInfo> newColumns = new ArrayList<>();
        for (TableSyncSource table : presentTables) {
            String tableKey = tableKey(table.tableName());
            TableInfo tableInfo = buildPhysicalTableInfo(datasourceId, table);
            TableInfo existingTable = existingTableIndex.get(tableKey);
            if (existingTable == null) {
                newTables.add(tableInfo);
            } else {
                tableInfo.setId(existingTable.getId());
                tableInfoMapper.updatePhysicalCacheFields(tableInfo);
            }

            Map<String, ColumnInfo> existingColumnIndex =
                    loadColumnIndex(columnsByTableName.getOrDefault(tableKey, List.of()));
            for (ColumnSyncSource column : table.columns()) {
                ColumnInfo columnInfo =
                        buildPhysicalColumnInfo(datasourceId, table.tableName(), column);
                ColumnInfo existingColumn = existingColumnIndex.get(columnKey(column.columnName()));
                if (existingColumn == null) {
                    newColumns.add(columnInfo);
                } else {
                    columnInfo.setId(existingColumn.getId());
                    columnSemanticInfoMapper.updatePhysicalCacheFields(columnInfo);
                }
            }
        }
        if (!newTables.isEmpty()) {
            tableInfoMapper.batchUpsertPhysicalCache(newTables);
        }
        if (!newColumns.isEmpty()) {
            columnSemanticInfoMapper.batchUpsertPhysicalCache(newColumns);
        }
    }

    private void markMissingTables(
            Integer datasourceId, List<String> tableNames, LocalDateTime now) {
        if (!tableNames.isEmpty()) {
            tableInfoMapper.markPhysicalMissingByDatasourceIdAndTableNames(
                    datasourceId, tableNames, now);
        }
    }

    private void markMissingColumns(
            Integer datasourceId, List<Integer> missingColumnIds, LocalDateTime now) {
        if (!missingColumnIds.isEmpty()) {
            columnSemanticInfoMapper.markPhysicalMissingByIds(datasourceId, missingColumnIds, now);
        }
    }

    private SyncTableResult buildPresentTableResult(
            TableSyncSource table,
            TableInfo existingTable,
            List<ColumnInfo> existingColumns,
            List<Integer> missingColumnIds) {
        // 使用写入前快照区分新增、恢复和更新，批量 upsert 后无需再次查询数据库。
        Map<String, ColumnInfo> existingColumnIndex = loadColumnIndex(existingColumns);
        Set<String> physicalColumnNames =
                table.columns().stream()
                        .map(ColumnSyncSource::columnName)
                        .map(this::columnKey)
                        .collect(Collectors.toSet());
        int addedColumns = 0;
        int reactivatedColumns = 0;
        int updatedColumns = 0;
        for (ColumnSyncSource column : table.columns()) {
            ColumnInfo existingColumn = existingColumnIndex.get(columnKey(column.columnName()));
            if (existingColumn == null) {
                addedColumns++;
                continue;
            }
            if (Boolean.FALSE.equals(existingColumn.getPhysicalStatus())) {
                reactivatedColumns++;
            }
            if (!Objects.equals(existingColumn.getPhysicalColumnDescription(), column.description())
                    || !Objects.equals(existingColumn.getTypeName(), column.typeName())
                    || !Objects.equals(existingColumn.getPrimaryKey(), column.primaryKey())
                    || !Objects.equals(existingColumn.getIndexInfo(), column.indexInfo())) {
                updatedColumns++;
            }
        }
        int missingColumnsMarked =
                collectMissingColumnIds(existingColumns, physicalColumnNames, missingColumnIds);
        return SyncTableResult.builder()
                .tableName(table.tableName())
                .physicalTableFound(true)
                .tableAdded(existingTable == null)
                .tableReactivated(
                        existingTable != null
                                && Boolean.FALSE.equals(existingTable.getPhysicalStatus()))
                .tableUpdated(
                        existingTable != null
                                && !Objects.equals(
                                        existingTable.getPhysicalTableDescription(),
                                        table.description()))
                .addedColumns(addedColumns)
                .reactivatedColumns(reactivatedColumns)
                .updatedColumns(updatedColumns)
                .missingColumnsMarked(missingColumnsMarked)
                .message("同步完成")
                .build();
    }

    private SyncTableResult buildMissingTableResult(
            String tableName,
            TableInfo existingTable,
            List<ColumnInfo> existingColumns,
            List<Integer> missingColumnIds) {
        int missingColumnsMarked =
                collectMissingColumnIds(existingColumns, Set.of(), missingColumnIds);
        return SyncTableResult.builder()
                .tableName(existingTable == null ? tableName : existingTable.getTableName())
                .physicalTableFound(false)
                .tableMarkedAsMissing(
                        existingTable != null
                                && !Boolean.FALSE.equals(existingTable.getPhysicalStatus()))
                .missingColumnsMarked(missingColumnsMarked)
                .message("物理表不存在")
                .build();
    }

    private String tableKey(String tableName) {
        return SemanticUtils.normalizeObjectName(tableName, "Missing tableName.");
    }

    private String columnKey(String columnName) {
        return SemanticUtils.normalizeObjectName(columnName, "Missing columnName.");
    }

    private Map<String, TableInfo> loadSemanticTableIndex(
            Integer datasourceId, List<String> tableNames) {
        Map<String, TableInfo> result = new LinkedHashMap<>();
        for (TableInfo table :
                tableInfoMapper.selectByDatasourceIdAndTableNames(datasourceId, tableNames)) {
            String tableName =
                    SemanticUtils.normalizeObjectName(
                            table.getTableName(), "Missing semantic tableName.");
            result.putIfAbsent(tableName, table);
        }
        return result;
    }

    private Map<String, List<ColumnInfo>> loadSemanticColumnsByTable(
            Integer datasourceId, List<String> tableNames) {
        Map<String, List<ColumnInfo>> result = new LinkedHashMap<>();
        for (ColumnInfo column :
                columnSemanticInfoMapper.selectByDatasourceIdAndTableNames(
                        datasourceId, tableNames)) {
            String tableName =
                    SemanticUtils.normalizeObjectName(
                            column.getTableName(), "Missing semantic tableName.");
            result.computeIfAbsent(tableName, _key -> new ArrayList<>()).add(column);
        }
        return result;
    }

    private Map<String, ColumnInfo> loadColumnIndex(List<ColumnInfo> columns) {
        Map<String, ColumnInfo> index = new LinkedHashMap<>();
        for (ColumnInfo column : columns) {
            index.putIfAbsent(
                    SemanticUtils.normalizeObjectName(
                            column.getColumnName(), "Missing semantic columnName."),
                    column);
        }
        return index;
    }

    private int collectMissingColumnIds(
            List<ColumnInfo> semanticColumns,
            Set<String> physicalColumnNames,
            List<Integer> missingColumnIds) {
        // 收集缺失列 ID 供后续一次性更新，同时返回当前表的缺失列数量。
        int missingColumnsMarked = 0;
        for (ColumnInfo column : semanticColumns) {
            String columnName =
                    SemanticUtils.normalizeObjectName(
                            column.getColumnName(), "Missing semantic columnName.");
            if (physicalColumnNames.contains(columnName)
                    || Boolean.FALSE.equals(column.getPhysicalStatus())
                    || column.getId() == null) {
                continue;
            }
            missingColumnIds.add(column.getId());
            missingColumnsMarked++;
        }
        return missingColumnsMarked;
    }

    private TableInfo buildPhysicalTableInfo(Integer datasourceId, TableSyncSource table) {
        TableInfo tableInfo = new TableInfo();
        tableInfo.setDatasourceId(datasourceId);
        tableInfo.setTableName(table.tableName());
        tableInfo.setPhysicalTableDescription(table.description());
        tableInfo.setTableDescription(table.description());
        tableInfo.setDomain(SemanticConstants.DEFAULT_DOMAIN);
        tableInfo.setIsVisible(Boolean.TRUE);
        tableInfo.setPhysicalStatus(Boolean.TRUE);
        return tableInfo;
    }

    private ColumnInfo buildPhysicalColumnInfo(
            Integer datasourceId, String tableName, ColumnSyncSource column) {
        ColumnInfo columnInfo = new ColumnInfo();
        columnInfo.setDatasourceId(datasourceId);
        columnInfo.setTableName(tableName);
        columnInfo.setColumnName(column.columnName());
        columnInfo.setPhysicalColumnDescription(column.description());
        columnInfo.setColumnDescription(column.description());
        columnInfo.setTypeName(column.typeName());
        columnInfo.setPrimaryKey(column.primaryKey());
        columnInfo.setIndexInfo(column.indexInfo());
        columnInfo.setIsVisible(Boolean.TRUE);
        columnInfo.setPhysicalStatus(Boolean.TRUE);
        return columnInfo;
    }

    public record TableSyncSource(
            String tableName, String description, List<ColumnSyncSource> columns) {}

    public record ColumnSyncSource(
            String columnName,
            String description,
            String typeName,
            Boolean primaryKey,
            String indexInfo) {}
}

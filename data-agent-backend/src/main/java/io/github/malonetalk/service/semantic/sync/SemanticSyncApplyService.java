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
import io.github.malonetalk.dto.datasource.PhysicalTableInfo;
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

@Service
@RequiredArgsConstructor
public class SemanticSyncApplyService {

    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;

    public List<SyncTableResult> applyTableSync(
            Integer datasourceId, List<TableSyncSource> presentTables, List<String> missingTables) {
        LocalDateTime now = LocalDateTime.now();
        Set<String> tableNames = new LinkedHashSet<>();
        presentTables.forEach(table -> tableNames.add(table.tableName()));
        tableNames.addAll(missingTables);

        Map<String, TableInfo> existingTableIndex =
                loadSemanticTableIndex(datasourceId, List.copyOf(tableNames));
        Map<String, List<ColumnInfo>> columnsByTableName =
                loadSemanticColumnsByTable(datasourceId, List.copyOf(tableNames));

        batchUpsertPresentTables(datasourceId, presentTables, now);
        batchUpsertPresentColumns(datasourceId, presentTables, now);

        List<Integer> missingColumnIds = new ArrayList<>();
        List<SyncTableResult> results = new ArrayList<>();
        for (TableSyncSource table : presentTables) {
            results.add(
                    buildPresentTableResult(
                            table,
                            existingTableIndex.get(table.tableName()),
                            columnsByTableName.getOrDefault(table.tableName(), List.of()),
                            missingColumnIds));
        }
        for (String missingTable : missingTables) {
            results.add(
                    buildMissingTableResult(
                            missingTable,
                            existingTableIndex.get(missingTable),
                            columnsByTableName.getOrDefault(missingTable, List.of()),
                            missingColumnIds));
        }

        markMissingTables(datasourceId, missingTables, existingTableIndex, now);
        markMissingColumns(datasourceId, missingColumnIds, now);
        return results;
    }

    public PhysicalStatusRefreshPlan buildPhysicalStatusRefreshPlan(
            Integer datasourceId,
            Map<String, PhysicalTableInfo> physicalTables,
            Map<String, Set<String>> physicalColumnNamesByTable) {
        List<TableInfo> semanticTables = tableInfoMapper.selectByDatasourceId(datasourceId);
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
                            tableInfo.getTableName(),
                            "Missing tableName while refreshing physical status.");
            List<ColumnInfo> columns =
                    columnsByTableName.getOrDefault(normalizedTableName, List.of());
            if (!physicalTables.containsKey(normalizedTableName)) {
                if (!Boolean.FALSE.equals(tableInfo.getPhysicalStatus())) {
                    tableNamesToMarkMissing.add(normalizedTableName);
                }
                results.add(
                        buildMissingTableResult(
                                normalizedTableName, tableInfo, columns, columnIdsToMarkMissing));
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

        return new PhysicalStatusRefreshPlan(
                datasourceId, tableNamesToMarkMissing, columnIdsToMarkMissing, results);
    }

    public List<SyncTableResult> applyPhysicalStatusRefresh(PhysicalStatusRefreshPlan refreshPlan) {
        LocalDateTime now = LocalDateTime.now();
        if (!refreshPlan.tableNamesToMarkMissing().isEmpty()) {
            tableInfoMapper.markPhysicalMissingByDatasourceIdAndTableNames(
                    refreshPlan.datasourceId(), refreshPlan.tableNamesToMarkMissing(), now);
        }
        markMissingColumns(refreshPlan.datasourceId(), refreshPlan.columnIdsToMarkMissing(), now);
        return refreshPlan.results();
    }

    private void batchUpsertPresentTables(
            Integer datasourceId, List<TableSyncSource> presentTables, LocalDateTime now) {
        // Upsert inserts new physical tables and reactivates existing rows while keeping semantic
        // fields owned by users intact.
        if (presentTables.isEmpty()) {
            return;
        }
        tableInfoMapper.batchUpsertPhysicalCache(
                presentTables.stream()
                        .map(table -> buildPhysicalTableInfo(datasourceId, table, now))
                        .toList());
    }

    private void batchUpsertPresentColumns(
            Integer datasourceId, List<TableSyncSource> presentTables, LocalDateTime now) {
        // Flatten all selected table columns so one batch write replaces per-column insert/update.
        List<ColumnInfo> physicalColumns =
                presentTables.stream()
                        .flatMap(table -> table.columns().stream())
                        .map(column -> buildPhysicalColumnInfo(datasourceId, column, now))
                        .toList();
        if (!physicalColumns.isEmpty()) {
            columnSemanticInfoMapper.batchUpsertPhysicalCache(physicalColumns);
        }
    }

    private void markMissingTables(
            Integer datasourceId,
            List<String> missingTables,
            Map<String, TableInfo> existingTableIndex,
            LocalDateTime now) {
        // Only persisted semantic rows can be marked missing; unknown physical names are reported
        // without creating new semantic records.
        List<String> tableNamesToMarkMissing =
                missingTables.stream()
                        .filter(
                                tableName ->
                                        existingTableIndex.containsKey(tableName)
                                                && !Boolean.FALSE.equals(
                                                        existingTableIndex
                                                                .get(tableName)
                                                                .getPhysicalStatus()))
                        .toList();
        if (!tableNamesToMarkMissing.isEmpty()) {
            tableInfoMapper.markPhysicalMissingByDatasourceIdAndTableNames(
                    datasourceId, tableNamesToMarkMissing, now);
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
        // Counts are derived from the pre-write snapshot so the response still distinguishes
        // added, reactivated, and updated rows after the batch upsert runs.
        Map<ColumnKey, ColumnInfo> existingColumnIndex = loadColumnIndex(existingColumns);
        Set<String> physicalColumnNames =
                table.columns().stream()
                        .map(ColumnSyncSource::columnName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        int addedColumns = 0;
        int reactivatedColumns = 0;
        int updatedColumns = 0;
        for (ColumnSyncSource column : table.columns()) {
            ColumnInfo existingColumn =
                    existingColumnIndex.get(new ColumnKey(column.tableName(), column.columnName()));
            if (existingColumn == null) {
                addedColumns++;
                continue;
            }
            if (Boolean.FALSE.equals(existingColumn.getPhysicalStatus())) {
                reactivatedColumns++;
            }
            if (isColumnPhysicalCacheChanged(existingColumn, column)) {
                updatedColumns++;
            }
        }
        int missingColumnsMarked =
                collectMissingColumnIds(existingColumns, physicalColumnNames, missingColumnIds);
        return SyncTableResult.builder()
                .tableName(table.displayName())
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

    private Map<String, TableInfo> loadSemanticTableIndex(
            Integer datasourceId, List<String> tableNames) {
        if (tableNames.isEmpty()) {
            return Map.of();
        }
        return tableInfoMapper.selectByDatasourceIdAndTableNames(datasourceId, tableNames).stream()
                .collect(
                        Collectors.toMap(
                                table ->
                                        SemanticUtils.normalizeObjectName(
                                                table.getTableName(),
                                                "Missing tableName while indexing semantic"
                                                        + " tables for sync."),
                                table -> table,
                                (left, _right) -> left,
                                LinkedHashMap::new));
    }

    private Map<String, List<ColumnInfo>> loadSemanticColumnsByTable(
            Integer datasourceId, List<String> tableNames) {
        if (tableNames.isEmpty()) {
            return Map.of();
        }
        return columnSemanticInfoMapper
                .selectByDatasourceIdAndTableNames(datasourceId, tableNames)
                .stream()
                .collect(
                        Collectors.groupingBy(
                                column ->
                                        SemanticUtils.normalizeObjectName(
                                                column.getTableName(),
                                                "Missing tableName while grouping semantic"
                                                        + " columns for sync."),
                                LinkedHashMap::new,
                                Collectors.toList()));
    }

    private Map<ColumnKey, ColumnInfo> loadColumnIndex(List<ColumnInfo> columns) {
        Map<ColumnKey, ColumnInfo> index = new LinkedHashMap<>();
        for (ColumnInfo column : columns) {
            index.put(
                    new ColumnKey(
                            SemanticUtils.normalizeObjectName(
                                    column.getTableName(),
                                    "Missing tableName while indexing semantic columns."),
                            SemanticUtils.normalizeObjectName(
                                    column.getColumnName(),
                                    "Missing columnName while indexing semantic columns.")),
                    column);
        }
        return index;
    }

    private int collectMissingColumnIds(
            List<ColumnInfo> semanticColumns,
            Set<String> physicalColumnNames,
            List<Integer> missingColumnIds) {
        // Collect ids for one batch update and return this table's count for the sync summary.
        int missingColumnsMarked = 0;
        for (ColumnInfo column : semanticColumns) {
            String columnName =
                    SemanticUtils.normalizeObjectName(
                            column.getColumnName(),
                            "Missing semantic columnName while collecting missing columns.");
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

    private boolean isColumnPhysicalCacheChanged(
            ColumnInfo existingColumn, ColumnSyncSource column) {
        return !Objects.equals(existingColumn.getPhysicalColumnDescription(), column.description())
                || !Objects.equals(existingColumn.getTypeName(), column.typeName())
                || !Objects.equals(existingColumn.getPrimaryKey(), column.primaryKey());
    }

    private TableInfo buildPhysicalTableInfo(
            Integer datasourceId, TableSyncSource table, LocalDateTime now) {
        TableInfo tableInfo = new TableInfo();
        tableInfo.setDatasourceId(datasourceId);
        tableInfo.setTableName(table.tableName());
        tableInfo.setPhysicalTableDescription(table.description());
        tableInfo.setDomain(SemanticConstants.DEFAULT_DOMAIN);
        tableInfo.setIsVisible(Boolean.TRUE);
        tableInfo.setPhysicalStatus(Boolean.TRUE);
        tableInfo.setCreateTime(now);
        tableInfo.setUpdateTime(now);
        return tableInfo;
    }

    private ColumnInfo buildPhysicalColumnInfo(
            Integer datasourceId, ColumnSyncSource column, LocalDateTime now) {
        ColumnInfo columnInfo = new ColumnInfo();
        columnInfo.setDatasourceId(datasourceId);
        columnInfo.setTableName(column.tableName());
        columnInfo.setColumnName(column.columnName());
        columnInfo.setPhysicalColumnDescription(column.description());
        columnInfo.setTypeName(column.typeName());
        columnInfo.setPrimaryKey(column.primaryKey());
        columnInfo.setIsVisible(Boolean.TRUE);
        columnInfo.setPhysicalStatus(Boolean.TRUE);
        columnInfo.setCreateTime(now);
        columnInfo.setUpdateTime(now);
        return columnInfo;
    }

    public record TableSyncSource(
            String tableName,
            String displayName,
            String description,
            List<ColumnSyncSource> columns) {}

    public record ColumnSyncSource(
            String tableName,
            String columnName,
            String description,
            String typeName,
            Boolean primaryKey) {}

    private record ColumnKey(String tableName, String columnName) {}

    public record PhysicalStatusRefreshPlan(
            Integer datasourceId,
            List<String> tableNamesToMarkMissing,
            List<Integer> columnIdsToMarkMissing,
            List<SyncTableResult> results) {}
}

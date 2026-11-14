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

import io.github.malonetalk.dto.semantic.SyncTableResult;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.model.bo.ColumnSyncSourceBo;
import io.github.malonetalk.model.bo.SemanticSyncDiffBo;
import io.github.malonetalk.model.bo.TableSyncSourceBo;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 基于写入前快照计算差异，不访问数据库或修改快照。 */
@Service
public class SemanticSyncDiffService {

    public SemanticSyncDiffBo compareTables(
            List<TableSyncSourceBo> presentTables,
            List<String> missingTables,
            Map<String, TableInfo> existingTableIndex,
            Map<String, List<ColumnInfo>> columnsByTableName) {
        // 基于写入前快照生成统计结果，同时收集需要批量标记缺失的列 ID。
        List<String> missingTableNames = new ArrayList<>();
        List<Integer> missingColumnIds = new ArrayList<>();
        List<SyncTableResult> results = new ArrayList<>();
        for (TableSyncSourceBo table : presentTables) {
            String tableKey = tableKey(table.tableName());
            results.add(
                    buildPresentTableResult(
                            table,
                            Optional.ofNullable(existingTableIndex.get(tableKey)),
                            columnsByTableName.getOrDefault(tableKey, List.of()),
                            missingColumnIds));
        }
        for (String missingTable : missingTables) {
            String tableKey = tableKey(missingTable);
            SyncTableResult result =
                    buildMissingTableResult(
                            missingTable,
                            Optional.ofNullable(existingTableIndex.get(tableKey)),
                            columnsByTableName.getOrDefault(tableKey, List.of()),
                            missingColumnIds);
            results.add(result);
            if (result.tableMarkedAsMissing()) {
                missingTableNames.add(missingTable);
            }
        }

        return new SemanticSyncDiffBo(results, missingTableNames, missingColumnIds);
    }

    public SemanticSyncDiffBo comparePhysicalStatus(
            List<TableInfo> semanticTables,
            Map<String, List<ColumnInfo>> columnsByTableName,
            Set<String> physicalTableNames,
            Map<String, Set<String>> physicalColumnNamesByTable) {
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
                                normalizedTableName,
                                Optional.of(tableInfo),
                                columns,
                                columnIdsToMarkMissing);
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

        return new SemanticSyncDiffBo(results, tableNamesToMarkMissing, columnIdsToMarkMissing);
    }

    private SyncTableResult buildPresentTableResult(
            TableSyncSourceBo table,
            Optional<TableInfo> existingTable,
            List<ColumnInfo> existingColumns,
            List<Integer> missingColumnIds) {
        // 使用写入前快照区分新增、恢复和更新，批量 upsert 后无需再次查询数据库。
        Map<String, ColumnInfo> existingColumnIndex = indexColumns(existingColumns);
        Set<String> physicalColumnNames =
                table.columns().stream()
                        .map(ColumnSyncSourceBo::columnName)
                        .map(this::columnKey)
                        .collect(Collectors.toSet());
        int addedColumns = 0;
        int reactivatedColumns = 0;
        int updatedColumns = 0;
        for (ColumnSyncSourceBo column : table.columns()) {
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
        boolean tableUpdated =
                existingTable
                        .map(
                                existing ->
                                        !Objects.equals(
                                                        existing.getObjectType(),
                                                        table.objectType())
                                                || !Objects.equals(
                                                        existing.getPhysicalTableDescription(),
                                                        table.description()))
                        .orElse(false);
        return SyncTableResult.builder()
                .tableName(table.tableName())
                .physicalTableFound(true)
                .tableAdded(existingTable.isEmpty())
                .tableReactivated(
                        existingTable
                                .map(TableInfo::getPhysicalStatus)
                                .filter(Boolean.FALSE::equals)
                                .isPresent())
                .tableUpdated(tableUpdated)
                .addedColumns(addedColumns)
                .reactivatedColumns(reactivatedColumns)
                .updatedColumns(updatedColumns)
                .missingColumnsMarked(missingColumnsMarked)
                .message("同步完成")
                .build();
    }

    private SyncTableResult buildMissingTableResult(
            String tableName,
            Optional<TableInfo> existingTable,
            List<ColumnInfo> existingColumns,
            List<Integer> missingColumnIds) {
        int missingColumnsMarked =
                collectMissingColumnIds(existingColumns, Set.of(), missingColumnIds);
        return SyncTableResult.builder()
                .tableName(existingTable.map(TableInfo::getTableName).orElse(tableName))
                .physicalTableFound(false)
                .tableMarkedAsMissing(
                        existingTable
                                .filter(
                                        existing ->
                                                !Boolean.FALSE.equals(existing.getPhysicalStatus()))
                                .isPresent())
                .missingColumnsMarked(missingColumnsMarked)
                .message("数据库对象不存在或当前连接账号不可见")
                .build();
    }

    Map<String, ColumnInfo> indexColumns(List<ColumnInfo> columns) {
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

    private String tableKey(String tableName) {
        return SemanticUtils.normalizeObjectName(tableName, "Missing tableName.");
    }

    private String columnKey(String columnName) {
        return SemanticUtils.normalizeObjectName(columnName, "Missing columnName.");
    }
}

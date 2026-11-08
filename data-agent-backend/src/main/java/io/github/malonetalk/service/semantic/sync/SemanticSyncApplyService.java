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
import io.github.malonetalk.model.bo.ColumnSyncSourceBo;
import io.github.malonetalk.model.bo.SemanticSyncDiffBo;
import io.github.malonetalk.model.bo.TableSyncSourceBo;
import io.github.malonetalk.model.converter.SemanticSyncConverter;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SemanticSyncApplyService {

    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final SemanticSyncDiffService semanticSyncDiffService;
    private final SemanticSyncConverter converter;

    @Transactional
    public List<SyncTableResult> applyTableSync(
            Integer datasourceId,
            List<TableSyncSourceBo> presentTables,
            List<String> missingTables) {
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

        SemanticSyncDiffBo diff =
                semanticSyncDiffService.compareTables(
                        presentTables, missingTables, existingTableIndex, columnsByTableName);

        markMissingTables(datasourceId, diff.missingTableNames(), now);
        markMissingColumns(diff.missingColumnIds(), now);
        return diff.results();
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
        List<String> tableNames = semanticTables.stream().map(TableInfo::getTableName).toList();
        Map<String, List<ColumnInfo>> columnsByTableName =
                loadSemanticColumnsByTable(datasourceId, tableNames);
        SemanticSyncDiffBo diff =
                semanticSyncDiffService.comparePhysicalStatus(
                        semanticTables,
                        columnsByTableName,
                        physicalTableNames,
                        physicalColumnNamesByTable);

        LocalDateTime now = LocalDateTime.now();
        markMissingTables(datasourceId, diff.missingTableNames(), now);
        markMissingColumns(diff.missingColumnIds(), now);
        return diff.results();
    }

    private void batchSavePresentSchema(
            Integer datasourceId,
            List<TableSyncSourceBo> presentTables,
            Map<String, TableInfo> existingTableIndex,
            Map<String, List<ColumnInfo>> columnsByTableName) {
        if (presentTables.isEmpty()) {
            return;
        }

        List<TableInfo> newTables = new ArrayList<>();
        for (TableSyncSourceBo table : presentTables) {
            String tableKey = tableKey(table.tableName());
            TableInfo tableInfo = converter.toTableInfo(datasourceId, table);
            tableInfo.setDomain(SemanticConstants.DEFAULT_DOMAIN);
            tableInfo.setIsVisible(Boolean.TRUE);
            tableInfo.setPhysicalStatus(Boolean.TRUE);
            TableInfo existingTable = existingTableIndex.get(tableKey);
            if (existingTable == null) {
                newTables.add(tableInfo);
            } else {
                tableInfo.setId(existingTable.getId());
                tableInfoMapper.updatePhysicalCacheFields(tableInfo);
                if (shouldFillDescription(
                        existingTable.getTableDescription(), table.description())) {
                    tableInfoMapper.fillSemanticDescriptionIfBlank(
                            existingTable.getId(), table.description());
                }
            }
        }
        if (!newTables.isEmpty()) {
            tableInfoMapper.batchUpsertPhysicalCache(newTables);
        }

        // 新表的主键由数据库生成，写入列之前需重新取得 table_id。
        List<String> presentTableNames =
                presentTables.stream().map(TableSyncSourceBo::tableName).toList();
        Map<String, TableInfo> persistedTableIndex =
                loadSemanticTableIndex(datasourceId, presentTableNames);
        List<ColumnInfo> newColumns = new ArrayList<>();
        for (TableSyncSourceBo table : presentTables) {
            String tableKey = tableKey(table.tableName());
            TableInfo persistedTable = persistedTableIndex.get(tableKey);
            Map<String, ColumnInfo> existingColumnIndex =
                    semanticSyncDiffService.indexColumns(
                            columnsByTableName.getOrDefault(tableKey, List.of()));
            for (ColumnSyncSourceBo column : table.columns()) {
                ColumnInfo columnInfo =
                        converter.toColumnInfo(datasourceId, persistedTable.getId(), column);
                columnInfo.setIsVisible(Boolean.TRUE);
                columnInfo.setPhysicalStatus(Boolean.TRUE);
                ColumnInfo existingColumn = existingColumnIndex.get(columnKey(column.columnName()));
                if (existingColumn == null) {
                    newColumns.add(columnInfo);
                } else {
                    columnInfo.setId(existingColumn.getId());
                    columnSemanticInfoMapper.updatePhysicalCacheFields(columnInfo);
                    if (shouldFillDescription(
                            existingColumn.getColumnDescription(), column.description())) {
                        columnSemanticInfoMapper.fillSemanticDescriptionIfBlank(
                                existingColumn.getId(), column.description());
                    }
                }
            }
        }
        if (!newColumns.isEmpty()) {
            columnSemanticInfoMapper.batchUpsertPhysicalCache(newColumns);
        }
    }

    private boolean shouldFillDescription(String current, String physical) {
        return (current == null || current.isEmpty()) && physical != null && !physical.isEmpty();
    }

    private void markMissingTables(
            Integer datasourceId, List<String> tableNames, LocalDateTime now) {
        if (!tableNames.isEmpty()) {
            tableInfoMapper.markPhysicalMissingByDatasourceIdAndTableNames(
                    datasourceId, tableNames, now);
        }
    }

    private void markMissingColumns(List<Integer> missingColumnIds, LocalDateTime now) {
        if (!missingColumnIds.isEmpty()) {
            columnSemanticInfoMapper.markPhysicalMissingByIds(missingColumnIds, now);
        }
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
}

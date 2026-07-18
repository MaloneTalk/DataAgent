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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SemanticSyncApplyService {

    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;

    public TableSyncDiff ensureTablePresent(
            Integer datasourceId,
            String normalizedTableName,
            String physicalTableDescription,
            LocalDateTime now) {
        TableInfo existingTable =
                tableInfoMapper.selectByDatasourceIdAndTableName(datasourceId, normalizedTableName);
        if (existingTable == null) {
            try {
                tableInfoMapper.insert(
                        buildNewTableInfo(
                                datasourceId, normalizedTableName, physicalTableDescription, now));
                return new TableSyncDiff(true, false, false);
            } catch (DuplicateKeyException ignored) {
                existingTable =
                        tableInfoMapper.selectByDatasourceIdAndTableName(
                                datasourceId, normalizedTableName);
            }
            // 并发冲突后仍查不到（极端 race / 事务可见性），放弃本次同步
            if (existingTable == null) {
                return new TableSyncDiff(false, false, false);
            }
        }

        boolean reactivated = Boolean.FALSE.equals(existingTable.getPhysicalStatus());
        boolean descriptionChanged =
                !Objects.equals(
                        existingTable.getPhysicalTableDescription(), physicalTableDescription);
        if (reactivated || descriptionChanged) {
            existingTable.setPhysicalTableDescription(physicalTableDescription);
            existingTable.setPhysicalStatus(Boolean.TRUE);
            existingTable.setUpdateTime(now);
            tableInfoMapper.updatePhysicalCacheFields(existingTable);
        }
        // 已存在路径：added 恒为 false，reactivated/updated 直接透传（塌缩三分支，行为不变）
        return new TableSyncDiff(false, reactivated, descriptionChanged);
    }

    public ColumnSyncDiff ensureColumnPresent(
            Integer datasourceId,
            String normalizedTableName,
            String normalizedColumnName,
            String physicalColumnDescription,
            String typeName,
            boolean primaryKey,
            LocalDateTime now,
            Map<String, ColumnInfo> semanticColumnMap) {
        ColumnInfo existingColumn = semanticColumnMap.get(normalizedColumnName);
        if (existingColumn == null) {
            // 列不存在：尝试插入；若并发插入已抢建则回退为重载（并回填 semanticColumnMap）
            try {
                columnSemanticInfoMapper.insert(
                        buildNewColumnInfo(
                                datasourceId,
                                normalizedTableName,
                                normalizedColumnName,
                                physicalColumnDescription,
                                typeName,
                                primaryKey,
                                now));
                return new ColumnSyncDiff(true, false, false);
            } catch (DuplicateKeyException ignored) {
                existingColumn =
                        loadColumnInsertedConcurrently(
                                datasourceId,
                                normalizedTableName,
                                normalizedColumnName,
                                semanticColumnMap);
            }
            // 并发冲突后仍查不到（极端 race / 事务可见性），放弃本次同步
            if (existingColumn == null) {
                return new ColumnSyncDiff(false, false, false);
            }
        }

        boolean reactivated = Boolean.FALSE.equals(existingColumn.getPhysicalStatus());
        boolean descriptionChanged =
                !Objects.equals(
                        existingColumn.getPhysicalColumnDescription(), physicalColumnDescription);
        boolean typeChanged = !Objects.equals(existingColumn.getTypeName(), typeName);
        boolean primaryKeyChanged = !Objects.equals(existingColumn.getPrimaryKey(), primaryKey);
        boolean updated = descriptionChanged || typeChanged || primaryKeyChanged;
        if (reactivated || updated) {
            existingColumn.setPhysicalColumnDescription(physicalColumnDescription);
            existingColumn.setTypeName(typeName);
            existingColumn.setPrimaryKey(primaryKey);
            existingColumn.setPhysicalStatus(Boolean.TRUE);
            existingColumn.setUpdateTime(now);
            columnSemanticInfoMapper.updatePhysicalCacheFields(existingColumn);
        }
        // 已存在路径：added 恒为 false，reactivated/updated 直接透传（塌缩三分支，行为不变）
        return new ColumnSyncDiff(false, reactivated, updated);
    }

    private ColumnInfo loadColumnInsertedConcurrently(
            Integer datasourceId,
            String normalizedTableName,
            String normalizedColumnName,
            Map<String, ColumnInfo> semanticColumnMap) {
        ColumnInfo existingColumn =
                columnSemanticInfoMapper.selectByDatasourceIdAndTableNameAndColumnName(
                        datasourceId, normalizedTableName, normalizedColumnName);
        if (existingColumn != null) {
            semanticColumnMap.put(normalizedColumnName, existingColumn);
        }
        return existingColumn;
    }

    public SyncTableResult markMissingTable(
            Integer datasourceId, String normalizedTableName, LocalDateTime now) {
        TableInfo existingTable =
                tableInfoMapper.selectByDatasourceIdAndTableName(datasourceId, normalizedTableName);
        if (existingTable == null) {
            // 语义层本无此表记录，无任何"标缺失"动作发生 → 事件标志为 false
            return buildMissingResult(normalizedTableName, false, 0);
        }

        boolean alreadyMissing = Boolean.FALSE.equals(existingTable.getPhysicalStatus());
        boolean tableMarkedAsMissing = !alreadyMissing;
        if (tableMarkedAsMissing) {
            existingTable.setPhysicalStatus(Boolean.FALSE);
            existingTable.setUpdateTime(now);
            tableInfoMapper.updatePhysicalCacheFields(existingTable);
        }
        // 整张表缺失 → 其下所有语义列一并标缺失（差量标缺失见 markMissingColumns）
        List<ColumnInfo> semanticColumns =
                columnSemanticInfoMapper.selectByDatasourceIdAndTableName(
                        datasourceId, normalizedTableName);
        int missingColumnsMarked = markAllColumnsMissing(semanticColumns, now);
        return buildMissingResult(
                existingTable.getTableName(), tableMarkedAsMissing, missingColumnsMarked);
    }

    private SyncTableResult buildMissingResult(
            String tableName, boolean tableMarkedAsMissing, int missingColumnsMarked) {
        return SyncTableResult.builder()
                .tableName(tableName)
                .physicalTableFound(false)
                .tableMarkedAsMissing(tableMarkedAsMissing)
                .missingColumnsMarked(missingColumnsMarked)
                .message("物理表不存在")
                .build();
    }

    public int markMissingColumns(
            List<ColumnInfo> semanticColumns, Set<String> physicalColumnNames, LocalDateTime now) {
        int missingColumnsMarked = 0;
        for (ColumnInfo semanticColumn : semanticColumns) {
            if (physicalColumnNames.contains(
                    SemanticUtils.normalizeObjectName(
                            semanticColumn.getColumnName(),
                            "Missing semantic columnName while marking missing columns."))) {
                continue;
            }
            if (Boolean.FALSE.equals(semanticColumn.getPhysicalStatus())) {
                continue;
            }
            semanticColumn.setPhysicalStatus(Boolean.FALSE);
            semanticColumn.setUpdateTime(now);
            columnSemanticInfoMapper.updatePhysicalCacheFields(semanticColumn);
            missingColumnsMarked++;
        }
        return missingColumnsMarked;
    }

    private int markAllColumnsMissing(List<ColumnInfo> semanticColumns, LocalDateTime now) {
        int missingColumnsMarked = 0;
        for (ColumnInfo semanticColumn : semanticColumns) {
            if (Boolean.FALSE.equals(semanticColumn.getPhysicalStatus())) {
                continue;
            }
            semanticColumn.setPhysicalStatus(Boolean.FALSE);
            semanticColumn.setUpdateTime(now);
            columnSemanticInfoMapper.updatePhysicalCacheFields(semanticColumn);
            missingColumnsMarked++;
        }
        return missingColumnsMarked;
    }

    private TableInfo buildNewTableInfo(
            Integer datasourceId,
            String normalizedTableName,
            String physicalTableDescription,
            LocalDateTime now) {
        TableInfo tableInfo = new TableInfo();
        tableInfo.setDatasourceId(datasourceId);
        tableInfo.setTableName(normalizedTableName);
        tableInfo.setPhysicalTableDescription(physicalTableDescription);
        tableInfo.setTableDescription(null);
        tableInfo.setDomain(SemanticConstants.DEFAULT_DOMAIN);
        tableInfo.setIsVisible(Boolean.TRUE);
        tableInfo.setPhysicalStatus(Boolean.TRUE);
        tableInfo.setCreateTime(now);
        tableInfo.setUpdateTime(now);
        return tableInfo;
    }

    private ColumnInfo buildNewColumnInfo(
            Integer datasourceId,
            String normalizedTableName,
            String normalizedColumnName,
            String physicalColumnDescription,
            String typeName,
            boolean primaryKey,
            LocalDateTime now) {
        ColumnInfo columnInfo = new ColumnInfo();
        columnInfo.setDatasourceId(datasourceId);
        columnInfo.setTableName(normalizedTableName);
        columnInfo.setColumnName(normalizedColumnName);
        columnInfo.setPhysicalColumnDescription(physicalColumnDescription);
        columnInfo.setTypeName(typeName);
        columnInfo.setPrimaryKey(primaryKey);
        columnInfo.setColumnDescription(null);
        columnInfo.setIsVisible(Boolean.TRUE);
        columnInfo.setPhysicalStatus(Boolean.TRUE);
        columnInfo.setCreateTime(now);
        columnInfo.setUpdateTime(now);
        return columnInfo;
    }

    public record TableSyncDiff(boolean added, boolean reactivated, boolean updated) {}

    public record ColumnSyncDiff(boolean added, boolean reactivated, boolean updated) {}
}

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
public class SemanticSyncDiffService {

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
        }
        if (existingTable == null) {
            return new TableSyncDiff(false, false, false);
        }

        boolean reactivated = Boolean.FALSE.equals(existingTable.getPhysicalStatus());
        boolean descriptionChanged =
                !Objects.equals(
                        existingTable.getPhysicalTableDescription(), physicalTableDescription);
        boolean updated = descriptionChanged;
        if (reactivated || descriptionChanged) {
            existingTable.setPhysicalTableDescription(physicalTableDescription);
            existingTable.setPhysicalStatus(Boolean.TRUE);
            existingTable.setUpdateTime(now);
            tableInfoMapper.updatePhysicalCacheFields(existingTable);
        }
        if (reactivated) {
            return new TableSyncDiff(false, true, updated);
        }
        if (updated) {
            return new TableSyncDiff(false, false, true);
        }
        return new TableSyncDiff(false, false, false);
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
        }
        if (existingColumn == null) {
            return new ColumnSyncDiff(false, false, false);
        }

        boolean reactivated = Boolean.FALSE.equals(existingColumn.getPhysicalStatus());
        boolean descriptionChanged =
                !Objects.equals(
                        existingColumn.getPhysicalColumnDescription(), physicalColumnDescription);
        boolean typeChanged = !Objects.equals(existingColumn.getTypeName(), typeName);
        boolean primaryKeyChanged =
                !Objects.equals(existingColumn.getPrimaryKey(), Boolean.valueOf(primaryKey));
        boolean updated = descriptionChanged || typeChanged || primaryKeyChanged;
        if (reactivated || updated) {
            existingColumn.setPhysicalColumnDescription(physicalColumnDescription);
            existingColumn.setTypeName(typeName);
            existingColumn.setPrimaryKey(primaryKey);
            existingColumn.setPhysicalStatus(Boolean.TRUE);
            existingColumn.setUpdateTime(now);
            columnSemanticInfoMapper.updatePhysicalCacheFields(existingColumn);
        }
        if (reactivated) {
            return new ColumnSyncDiff(false, true, updated);
        }
        if (updated) {
            return new ColumnSyncDiff(false, false, true);
        }
        return new ColumnSyncDiff(false, false, false);
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
            return new SyncTableResult(
                    normalizedTableName, false, false, false, false, false, 0, 0, 0, 0, "物理表不存在");
        }

        boolean tableMarkedMissing = !Boolean.FALSE.equals(existingTable.getPhysicalStatus());
        if (tableMarkedMissing) {
            existingTable.setPhysicalStatus(Boolean.FALSE);
            existingTable.setUpdateTime(now);
            tableInfoMapper.updatePhysicalCacheFields(existingTable);
        }
        int missingColumnsMarked =
                markAllColumnsMissing(
                        columnSemanticInfoMapper.selectByDatasourceIdAndTableName(
                                datasourceId, normalizedTableName),
                        now);
        return new SyncTableResult(
                existingTable.getTableName(),
                false,
                false,
                false,
                false,
                tableMarkedMissing,
                0,
                0,
                0,
                missingColumnsMarked,
                "物理表不存在");
    }

    public int markMissingColumns(
            List<ColumnInfo> semanticColumns, Set<String> physicalColumnNames, LocalDateTime now) {
        int missingColumnsMarked = 0;
        for (ColumnInfo semanticColumn : semanticColumns) {
            if (physicalColumnNames.contains(
                    SemanticUtils.normalizeObjectName(semanticColumn.getColumnName()))) {
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

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
package io.github.malonetalk.service;

import io.github.malonetalk.agent.datasource.ColumnInfo;
import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.entity.ColumnSemanticInfo;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.TableInfo;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SemanticVisibilitySupport {

    private final TableInfoService tableInfoService;
    private final ColumnSemanticInfoService columnSemanticInfoService;
    private final SchemaReader schemaReader;

    public SemanticVisibilitySupport(
            TableInfoService tableInfoService,
            ColumnSemanticInfoService columnSemanticInfoService,
            SchemaReader schemaReader) {
        this.tableInfoService = tableInfoService;
        this.columnSemanticInfoService = columnSemanticInfoService;
        this.schemaReader = schemaReader;
    }

    public VisibilityContext createVisibilityContext(Datasource datasource) {
        return new VisibilityContext(datasource);
    }

    public List<TableMergeSnapshot> mergeTables(Datasource datasource) {
        return createVisibilityContext(datasource).mergeTables();
    }

    public TableMergeSnapshot findMergedTable(Datasource datasource, String tableName) {
        return createVisibilityContext(datasource).findMergedTable(tableName);
    }

    public List<ColumnMergeSnapshot> mergeColumns(Datasource datasource, String tableName) {
        return createVisibilityContext(datasource).mergeColumns(tableName);
    }

    public ColumnMergeSnapshot findMergedColumn(
            Datasource datasource, String tableName, String columnName) {
        return createVisibilityContext(datasource).findMergedColumn(tableName, columnName);
    }

    public boolean isTableVisible(TableInfo semanticTable, TableInfo physicalTable) {
        if (semanticTable != null) {
            return Boolean.TRUE.equals(semanticTable.getIsVisible())
                    && Boolean.TRUE.equals(semanticTable.getIsActive());
        }
        return physicalTable != null && Boolean.TRUE.equals(physicalTable.getIsVisible());
    }

    public boolean isColumnVisible(ColumnSemanticInfo semanticColumn, ColumnInfo physicalColumn) {
        if (semanticColumn != null) {
            return Boolean.TRUE.equals(semanticColumn.getIsVisible())
                    && Boolean.TRUE.equals(semanticColumn.getIsActive());
        }
        return physicalColumn != null;
    }

    public String resolveTableName(TableMergeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        if (snapshot.physicalTable() != null) {
            return snapshot.physicalTable().getTableName();
        }
        if (snapshot.semanticTable() != null) {
            return snapshot.semanticTable().getTableName();
        }
        return null;
    }

    public String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private LinkedHashMap<String, TableMergeSnapshot> buildMergedTableMap(Datasource datasource) {
        List<TableInfo> physicalTables = schemaReader.getTables(datasource);
        List<TableInfo> semanticTables = tableInfoService.findByDatasourceId(datasource.getId());
        LinkedHashMap<String, TableMergeSnapshot> mergedTables = new LinkedHashMap<>();

        for (TableInfo physicalTable : physicalTables) {
            mergedTables.put(
                    normalizeName(physicalTable.getTableName()),
                    new TableMergeSnapshot(physicalTable, null));
        }

        for (TableInfo semanticTable : semanticTables) {
            TableMergeSnapshot existing =
                    mergedTables.get(normalizeName(semanticTable.getTableName()));
            if (existing == null) {
                continue;
            }
            mergedTables.put(
                    normalizeName(semanticTable.getTableName()),
                    new TableMergeSnapshot(
                            existing.physicalTable(),
                            selectPreferredSemanticTable(existing.semanticTable(), semanticTable)));
        }

        return mergedTables;
    }

    private List<ColumnMergeSnapshot> buildMergedColumns(
            Datasource datasource, String resolvedTableName) {
        if (resolvedTableName == null || resolvedTableName.isBlank()) {
            return Collections.emptyList();
        }
        List<ColumnInfo> physicalColumns =
                schemaReader.getTableSchema(datasource, resolvedTableName);
        List<ColumnSemanticInfo> semanticColumns =
                columnSemanticInfoService.findByDatasourceIdAndTableName(
                        datasource.getId(), resolvedTableName);
        LinkedHashMap<String, ColumnMergeSnapshot> mergedColumns = new LinkedHashMap<>();

        for (ColumnInfo physicalColumn : physicalColumns) {
            mergedColumns.put(
                    normalizeName(physicalColumn.getColumnName()),
                    new ColumnMergeSnapshot(physicalColumn, null));
        }

        for (ColumnSemanticInfo semanticColumn : semanticColumns) {
            ColumnMergeSnapshot existing =
                    mergedColumns.get(normalizeName(semanticColumn.getColumnName()));
            if (existing == null) {
                continue;
            }
            ColumnSemanticInfo preferredSemanticColumn =
                    selectPreferredSemanticColumn(existing.semanticColumn(), semanticColumn);
            mergedColumns.put(
                    normalizeName(semanticColumn.getColumnName()),
                    new ColumnMergeSnapshot(existing.physicalColumn(), preferredSemanticColumn));
        }

        return List.copyOf(mergedColumns.values());
    }

    private TableInfo selectPreferredSemanticTable(TableInfo existing, TableInfo candidate) {
        if (existing == null) {
            return candidate;
        }
        LocalDateTime existingUpdateTime =
                existing.getUpdateTime() != null
                        ? existing.getUpdateTime()
                        : existing.getCreateTime();
        LocalDateTime candidateUpdateTime =
                candidate.getUpdateTime() != null
                        ? candidate.getUpdateTime()
                        : candidate.getCreateTime();
        if (candidateUpdateTime == null) {
            return existing;
        }
        if (existingUpdateTime == null || candidateUpdateTime.isAfter(existingUpdateTime)) {
            return candidate;
        }
        return existing;
    }

    private ColumnSemanticInfo selectPreferredSemanticColumn(
            ColumnSemanticInfo existing, ColumnSemanticInfo candidate) {
        if (existing == null) {
            return candidate;
        }
        LocalDateTime existingUpdateTime =
                existing.getUpdateTime() != null
                        ? existing.getUpdateTime()
                        : existing.getCreateTime();
        LocalDateTime candidateUpdateTime =
                candidate.getUpdateTime() != null
                        ? candidate.getUpdateTime()
                        : candidate.getCreateTime();
        if (candidateUpdateTime == null) {
            return existing;
        }
        if (existingUpdateTime == null || candidateUpdateTime.isAfter(existingUpdateTime)) {
            return candidate;
        }
        return existing;
    }

    public final class VisibilityContext {

        private final Datasource datasource;
        private final LinkedHashMap<String, TableMergeSnapshot> mergedTablesByName;
        private final Map<String, List<ColumnMergeSnapshot>> mergedColumnsByTableName =
                new HashMap<>();
        private final Map<String, Map<String, ColumnMergeSnapshot>> mergedColumnLookupByTableName =
                new HashMap<>();

        private VisibilityContext(Datasource datasource) {
            this.datasource = datasource;
            this.mergedTablesByName = buildMergedTableMap(datasource);
        }

        public List<TableMergeSnapshot> mergeTables() {
            return new ArrayList<>(mergedTablesByName.values());
        }

        public TableMergeSnapshot findMergedTable(String tableName) {
            return mergedTablesByName.get(normalizeName(tableName));
        }

        public List<ColumnMergeSnapshot> mergeColumns(String tableName) {
            String tableKey = normalizeName(tableName);
            if (tableKey.isBlank()) {
                return Collections.emptyList();
            }
            List<ColumnMergeSnapshot> cachedColumns = mergedColumnsByTableName.get(tableKey);
            if (cachedColumns != null) {
                return cachedColumns;
            }
            TableMergeSnapshot tableSnapshot = findMergedTable(tableName);
            if (tableSnapshot == null || tableSnapshot.physicalTable() == null) {
                return Collections.emptyList();
            }
            String resolvedTableName = resolveTableName(tableSnapshot);
            List<ColumnMergeSnapshot> mergedColumns =
                    buildMergedColumns(datasource, resolvedTableName);
            mergedColumnsByTableName.put(tableKey, mergedColumns);

            Map<String, ColumnMergeSnapshot> columnLookup = new HashMap<>();
            for (ColumnMergeSnapshot mergedColumn : mergedColumns) {
                columnLookup.put(normalizeName(resolveColumnName(mergedColumn)), mergedColumn);
            }
            mergedColumnLookupByTableName.put(tableKey, columnLookup);
            String resolvedTableKey = normalizeName(resolveTableName(tableSnapshot));
            if (!resolvedTableKey.equals(tableKey)) {
                mergedColumnsByTableName.put(resolvedTableKey, mergedColumns);
                mergedColumnLookupByTableName.put(resolvedTableKey, columnLookup);
            }
            return mergedColumns;
        }

        public ColumnMergeSnapshot findMergedColumn(String tableName, String columnName) {
            String tableKey = normalizeName(tableName);
            String columnKey = normalizeName(columnName);
            if (tableKey.isBlank() || columnKey.isBlank()) {
                return null;
            }
            mergeColumns(tableName);
            Map<String, ColumnMergeSnapshot> columnLookup =
                    mergedColumnLookupByTableName.get(tableKey);
            if (columnLookup == null) {
                return null;
            }
            return columnLookup.get(columnKey);
        }

        private String resolveColumnName(ColumnMergeSnapshot mergedColumn) {
            if (mergedColumn.physicalColumn() != null) {
                return mergedColumn.physicalColumn().getColumnName();
            }
            if (mergedColumn.semanticColumn() != null) {
                return mergedColumn.semanticColumn().getColumnName();
            }
            return "";
        }
    }

    public record TableMergeSnapshot(TableInfo physicalTable, TableInfo semanticTable) {}

    public record ColumnMergeSnapshot(
            ColumnInfo physicalColumn, ColumnSemanticInfo semanticColumn) {}
}

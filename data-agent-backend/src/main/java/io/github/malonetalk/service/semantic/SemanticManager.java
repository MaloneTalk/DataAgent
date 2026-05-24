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
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.service.semantic.column.ColumnSemanticRepository;
import io.github.malonetalk.service.semantic.table.TableSemanticRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SemanticManager {

    private final TableSemanticRepository tableSemanticRepository;
    private final ColumnSemanticRepository columnSemanticRepository;
    private final SchemaReader schemaReader;

    public SemanticManager(
            TableSemanticRepository tableSemanticRepository,
            ColumnSemanticRepository columnSemanticRepository,
            SchemaReader schemaReader) {
        this.tableSemanticRepository = tableSemanticRepository;
        this.columnSemanticRepository = columnSemanticRepository;
        this.schemaReader = schemaReader;
    }

    public VisibilityContext createVisibilityContext(Datasource datasource) {
        return new VisibilityContext(datasource);
    }

    public boolean isTableVisible(TableInfo semanticTable, TableInfo physicalTable) {
        if (semanticTable != null) {
            return Boolean.TRUE.equals(semanticTable.getIsVisible())
                    && Boolean.TRUE.equals(semanticTable.getIsActive());
        }
        return physicalTable != null && Boolean.TRUE.equals(physicalTable.getIsVisible());
    }

    public boolean isColumnVisible(
            ColumnInfo semanticColumn,
            io.github.malonetalk.agent.datasource.ColumnInfo physicalColumn) {
        if (physicalColumn == null) {
            return false;
        }
        if (semanticColumn != null) {
            return Boolean.TRUE.equals(semanticColumn.getIsVisible())
                    && Boolean.TRUE.equals(semanticColumn.getIsActive());
        }
        return true;
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
        List<TableInfo> semanticTables =
                tableSemanticRepository.listByDatasourceId(datasource.getId());
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
                mergedTables.put(
                        normalizeName(semanticTable.getTableName()),
                        new TableMergeSnapshot(null, semanticTable));
                continue;
            }
            mergedTables.put(
                    normalizeName(semanticTable.getTableName()),
                    new TableMergeSnapshot(existing.physicalTable(), semanticTable));
        }

        return mergedTables;
    }

    private List<ColumnMergeSnapshot> buildMergedColumns(
            Datasource datasource, String resolvedTableName) {
        if (resolvedTableName == null || resolvedTableName.isBlank()) {
            return Collections.emptyList();
        }
        List<io.github.malonetalk.agent.datasource.ColumnInfo> physicalColumns =
                schemaReader.getTableSchema(datasource, resolvedTableName);
        List<ColumnInfo> semanticColumns =
                columnSemanticRepository.listByDatasourceIdAndTableName(
                        datasource.getId(), resolvedTableName);
        LinkedHashMap<String, ColumnMergeSnapshot> mergedColumns = new LinkedHashMap<>();

        for (io.github.malonetalk.agent.datasource.ColumnInfo physicalColumn : physicalColumns) {
            mergedColumns.put(
                    normalizeName(physicalColumn.getColumnName()),
                    new ColumnMergeSnapshot(physicalColumn, null));
        }

        for (ColumnInfo semanticColumn : semanticColumns) {
            ColumnMergeSnapshot existing =
                    mergedColumns.get(normalizeName(semanticColumn.getColumnName()));
            if (existing == null) {
                mergedColumns.put(
                        normalizeName(semanticColumn.getColumnName()),
                        new ColumnMergeSnapshot(null, semanticColumn));
                continue;
            }
            mergedColumns.put(
                    normalizeName(semanticColumn.getColumnName()),
                    new ColumnMergeSnapshot(existing.physicalColumn(), semanticColumn));
        }

        return List.copyOf(mergedColumns.values());
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
            if (tableSnapshot == null) {
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
            io.github.malonetalk.agent.datasource.ColumnInfo physicalColumn,
            ColumnInfo semanticColumn) {}
}

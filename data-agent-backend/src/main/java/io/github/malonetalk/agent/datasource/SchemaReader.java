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
package io.github.malonetalk.agent.datasource;

import io.github.malonetalk.dto.datasource.PhysicalColumnInfo;
import io.github.malonetalk.dto.datasource.PhysicalTableInfo;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.converter.JdbcColumnMetadataConverter;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.csv.CsvAccessService;
import io.github.malonetalk.utils.SemanticUtils;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class SchemaReader {

    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final JdbcColumnMetadataConverter columnMetadataConverter;
    private final CsvAccessService csvAccess;

    public List<PhysicalTableInfo> getTables(DatasourcePo datasource) {
        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            var ready = csvAccess.readyFilter(datasource);
            return getTables(conn).stream().filter(table -> ready.test(table.tableName())).toList();
        } catch (SQLException e) {
            log.error("Failed to read tables: {}", e.getMessage(), e);
            throw BusinessException.of(
                    ErrorCode.SCHEMA_READ_FAILED,
                    ErrorCode.SCHEMA_READ_FAILED.getDefaultMessage(),
                    e);
        }
    }

    public List<PhysicalColumnInfo> getTableSchema(
            DatasourcePo datasource, PhysicalTableInfo table) {
        csvAccess.requireReady(datasource, table.tableName());
        return readTableSchema(datasource, table.tableName(), "VIEW".equals(table.objectType()));
    }

    public List<PhysicalColumnInfo> getImportTableSchema(
            DatasourcePo datasource, CsvImportPo task) {
        csvAccess.requireImportTable(datasource, task);
        return readTableSchema(datasource, task.getPhysicalTableName(), false);
    }

    private List<PhysicalColumnInfo> readTableSchema(
            DatasourcePo datasource, String tableName, boolean view) {
        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            List<String> primaryKeys = view ? List.of() : getPrimaryKeys(conn, tableName);
            Map<String, List<String>> indexesByColumn =
                    view ? Map.of() : getIndexesByColumn(conn, tableName, primaryKeys);
            return getColumns(conn, tableName, primaryKeys, indexesByColumn);
        } catch (SQLException e) {
            log.error("Failed to read schema for table {}: {}", tableName, e.getMessage(), e);
            throw BusinessException.of(
                    ErrorCode.SCHEMA_READ_FAILED,
                    ErrorCode.SCHEMA_READ_FAILED.getDefaultMessage(),
                    e);
        }
    }

    public Map<String, Set<String>> getTableColumnNames(
            DatasourcePo datasource, Collection<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> columnNamesByTable = new LinkedHashMap<>();
        var ready = csvAccess.readyFilter(datasource);
        for (String tableName : tableNames) {
            if (!ready.test(tableName)) continue;
            columnNamesByTable.putIfAbsent(tableName, new LinkedHashSet<>());
        }

        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs =
                    metaData.getColumns(
                            conn.getCatalog(), schemaPattern(conn).orElse(null), "%", null)) {
                while (rs.next()) {
                    Set<String> columnNames = columnNamesByTable.get(rs.getString("TABLE_NAME"));
                    if (columnNames != null) {
                        columnNames.add(rs.getString("COLUMN_NAME"));
                    }
                }
            }
            return columnNamesByTable;
        } catch (SQLException e) {
            log.error("Failed to read column names for tables: {}", e.getMessage(), e);
            throw BusinessException.of(
                    ErrorCode.SCHEMA_READ_FAILED,
                    ErrorCode.SCHEMA_READ_FAILED.getDefaultMessage(),
                    e);
        }
    }

    private List<PhysicalTableInfo> getTables(Connection conn) throws SQLException {
        List<PhysicalTableInfo> tables = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs =
                metaData.getTables(
                        conn.getCatalog(),
                        schemaPattern(conn).orElse(null),
                        "%",
                        new String[] {"TABLE", "VIEW"})) {
            while (rs.next()) {
                tables.add(
                        new PhysicalTableInfo(
                                rs.getString("TABLE_NAME"),
                                rs.getString("REMARKS"),
                                rs.getString("TABLE_TYPE")));
            }
        }

        return tables;
    }

    private List<String> getPrimaryKeys(Connection conn, String tableName) throws SQLException {
        Map<Short, String> pkColumns = new TreeMap<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs =
                metaData.getPrimaryKeys(conn.getCatalog(), conn.getSchema(), tableName)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName != null) {
                    pkColumns.put(rs.getShort("KEY_SEQ"), normalizeColumnName(columnName));
                }
            }
        }

        return List.copyOf(pkColumns.values());
    }

    private Map<String, List<String>> getIndexesByColumn(
            Connection conn, String tableName, List<String> primaryKeys) throws SQLException {
        Map<String, IndexParts> indexes = new LinkedHashMap<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs =
                metaData.getIndexInfo(
                        conn.getCatalog(), conn.getSchema(), tableName, false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) {
                    continue;
                }
                boolean unique = !rs.getBoolean("NON_UNIQUE");
                short position = rs.getShort("ORDINAL_POSITION");
                indexes.computeIfAbsent(indexName, key -> new IndexParts(indexName, unique))
                        .columns()
                        .put(position, columnName);
            }
        }

        Map<String, List<String>> indexesByColumn = new LinkedHashMap<>();
        for (IndexParts index : indexes.values()) {
            if (!primaryKeys.isEmpty() && index.normalizedColumnNames().equals(primaryKeys)) {
                continue;
            }
            String description = index.description();
            for (String column : index.columns().values()) {
                indexesByColumn
                        .computeIfAbsent(normalizeColumnName(column), key -> new ArrayList<>())
                        .add(description);
            }
        }
        return indexesByColumn;
    }

    private List<PhysicalColumnInfo> getColumns(
            Connection conn,
            String tableName,
            List<String> primaryKeys,
            Map<String, List<String>> indexesByColumn)
            throws SQLException {
        List<PhysicalColumnInfo> columns = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs =
                metaData.getColumns(
                        conn.getCatalog(),
                        schemaPattern(conn).orElse(null),
                        escapeMetadataPattern(tableName, metaData.getSearchStringEscape()),
                        null)) {
            while (rs.next()) {
                if (!tableName.equals(rs.getString("TABLE_NAME"))) {
                    continue;
                }
                columns.add(columnMetadataConverter.convert(rs, primaryKeys, indexesByColumn));
            }
        }

        return columns;
    }

    private Optional<String> schemaPattern(Connection conn) throws SQLException {
        String escape = conn.getMetaData().getSearchStringEscape();
        return Optional.ofNullable(conn.getSchema())
                .map(schema -> escapeMetadataPattern(schema, escape));
    }

    private static String escapeMetadataPattern(String name, String escape) {
        if (escape == null || escape.isEmpty()) {
            return name;
        }
        return name.replace(escape, escape + escape)
                .replace("_", escape + "_")
                .replace("%", escape + "%");
    }

    private static String normalizeColumnName(String columnName) {
        return SemanticUtils.normalizeObjectName(
                columnName, "Missing column name while reading schema.");
    }

    private record IndexParts(String name, boolean unique, Map<Short, String> columns) {

        private IndexParts(String name, boolean unique) {
            this(name, unique, new TreeMap<>());
        }

        private String description() {
            return (unique ? "UNIQUE " : "")
                    + name
                    + "("
                    + String.join(", ", columns.values())
                    + ")";
        }

        private List<String> normalizedColumnNames() {
            return columns.values().stream().map(SchemaReader::normalizeColumnName).toList();
        }
    }
}

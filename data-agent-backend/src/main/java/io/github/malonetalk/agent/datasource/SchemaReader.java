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
import io.github.malonetalk.entity.Datasource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class SchemaReader {

    private final DynamicDataSourceManager dynamicDataSourceManager;

    public List<PhysicalTableInfo> getTables(Datasource datasource) {
        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            return getTables(conn);
        } catch (SQLException e) {
            log.error("Failed to read tables: {}", e.getMessage(), e);
            throw new SchemaReadException("Failed to read tables: " + e.getMessage(), e);
        }
    }

    public PhysicalTablePage getTablePage(
            Datasource datasource,
            String keyword,
            boolean sortDescending,
            int pageNumber,
            int pageSize) {
        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            return getNativeTablePage(
                            conn, datasource, keyword, sortDescending, pageNumber, pageSize)
                    .orElseGet(
                            () ->
                                    getMemoryTablePage(
                                            conn, keyword, sortDescending, pageNumber, pageSize));
        } catch (SQLException e) {
            log.error("Failed to read table page: {}", e.getMessage(), e);
            throw new SchemaReadException("Failed to read table page: " + e.getMessage(), e);
        }
    }

    public List<PhysicalColumnInfo> getTableSchema(Datasource datasource, String tableName) {
        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            Set<String> primaryKeys = getPrimaryKeys(conn, tableName);
            return getColumns(conn, tableName, primaryKeys);
        } catch (SQLException e) {
            log.error("Failed to read schema for table {}: {}", tableName, e.getMessage(), e);
            throw new SchemaReadException(
                    "Failed to read schema for table " + tableName + ": " + e.getMessage(), e);
        }
    }

    public Map<String, Set<String>> getTableColumnNames(
            Datasource datasource, Collection<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> columnNamesByTable = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            columnNamesByTable.putIfAbsent(tableName, new LinkedHashSet<>());
        }

        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs =
                    metaData.getColumns(conn.getCatalog(), conn.getSchema(), "%", null)) {
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
            throw new SchemaReadException("Failed to read column names: " + e.getMessage(), e);
        }
    }

    private List<PhysicalTableInfo> getTables(Connection conn) throws SQLException {
        List<PhysicalTableInfo> tables = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs =
                metaData.getTables(
                        conn.getCatalog(), conn.getSchema(), "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                tables.add(
                        new PhysicalTableInfo(rs.getString("TABLE_NAME"), rs.getString("REMARKS")));
            }
        }

        return tables;
    }

    private Optional<PhysicalTablePage> getNativeTablePage(
            Connection conn,
            Datasource datasource,
            String keyword,
            boolean sortDescending,
            int pageNumber,
            int pageSize) {
        // Keep filtering and paging close to the source when the database can do it cheaply.
        if (DataSourceType.fromCode(datasource.getType()).orElse(null) != DataSourceType.MYSQL) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    getMySqlTablePage(conn, keyword, sortDescending, pageNumber, pageSize));
        } catch (SQLException e) {
            log.warn("Fallback to metadata table paging: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private PhysicalTablePage getMySqlTablePage(
            Connection conn, String keyword, boolean sortDescending, int pageNumber, int pageSize)
            throws SQLException {
        // MySQL exposes table remarks in information_schema, so keyword search can cover both
        // names and comments without loading every table into the service layer.
        String normalizedKeyword = normalizeKeyword(keyword);
        String keywordClause =
                normalizedKeyword == null
                        ? ""
                        : " AND (LOWER(table_name) LIKE ? OR LOWER(table_comment) LIKE ?)";
        String orderDirection = sortDescending ? "DESC" : "ASC";
        long total = countMySqlTables(conn, normalizedKeyword, keywordClause);
        String sql =
                "SELECT table_name, table_comment FROM information_schema.tables"
                        + " WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'"
                        + keywordClause
                        + " ORDER BY table_name "
                        + orderDirection
                        + " LIMIT ? OFFSET ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int parameterIndex = bindKeyword(ps, normalizedKeyword, 1);
            ps.setInt(parameterIndex++, pageSize);
            ps.setInt(parameterIndex, (pageNumber - 1) * pageSize);
            List<PhysicalTableInfo> tables = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(
                            new PhysicalTableInfo(
                                    rs.getString("table_name"), rs.getString("table_comment")));
                }
            }
            return new PhysicalTablePage(tables, total, pageNumber, pageSize);
        }
    }

    private long countMySqlTables(Connection conn, String keyword, String keywordClause)
            throws SQLException {
        String sql =
                "SELECT COUNT(1) FROM information_schema.tables"
                        + " WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'"
                        + keywordClause;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindKeyword(ps, keyword, 1);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private int bindKeyword(PreparedStatement ps, String keyword, int parameterIndex)
            throws SQLException {
        // The same LIKE pattern is used for table name and table comment predicates.
        if (keyword == null) {
            return parameterIndex;
        }
        String pattern = "%" + keyword + "%";
        ps.setString(parameterIndex++, pattern);
        ps.setString(parameterIndex++, pattern);
        return parameterIndex;
    }

    private PhysicalTablePage getMemoryTablePage(
            Connection conn, String keyword, boolean sortDescending, int pageNumber, int pageSize) {
        // Fallback for drivers that do not support native table paging through catalog tables.
        try {
            List<PhysicalTableInfo> filtered =
                    getTables(conn).stream()
                            .filter(table -> matchesKeyword(table, keyword))
                            .sorted(buildTableComparator(sortDescending))
                            .toList();
            int fromIndex = Math.min((pageNumber - 1) * pageSize, filtered.size());
            int toIndex = Math.min(fromIndex + pageSize, filtered.size());
            return new PhysicalTablePage(
                    filtered.subList(fromIndex, toIndex), filtered.size(), pageNumber, pageSize);
        } catch (SQLException e) {
            throw new SchemaReadException("Failed to read tables: " + e.getMessage(), e);
        }
    }

    private boolean matchesKeyword(PhysicalTableInfo table, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword == null) {
            return true;
        }
        return containsIgnoreCase(table.tableName(), normalizedKeyword)
                || containsIgnoreCase(table.remarks(), normalizedKeyword);
    }

    private Comparator<PhysicalTableInfo> buildTableComparator(boolean sortDescending) {
        Comparator<PhysicalTableInfo> comparator =
                Comparator.comparing(
                        table -> normalizeObjectName(table.tableName()), Comparator.naturalOrder());
        return sortDescending ? comparator.reversed() : comparator;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase();
    }

    private String normalizeObjectName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing physical tableName while paging tables.");
        }
        return value.trim().toLowerCase();
    }

    private Set<String> getPrimaryKeys(Connection conn, String tableName) throws SQLException {
        Set<String> pkColumns = new HashSet<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs =
                metaData.getPrimaryKeys(conn.getCatalog(), conn.getSchema(), tableName)) {
            while (rs.next()) {
                pkColumns.add(rs.getString("COLUMN_NAME"));
            }
        }

        return pkColumns;
    }

    private List<PhysicalColumnInfo> getColumns(
            Connection conn, String tableName, Set<String> primaryKeys) throws SQLException {
        List<PhysicalColumnInfo> columns = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs =
                metaData.getColumns(conn.getCatalog(), conn.getSchema(), tableName, null)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String typeName = rs.getString("TYPE_NAME");
                int columnSize = rs.getInt("COLUMN_SIZE");
                String nullableStr = rs.getString("IS_NULLABLE");
                boolean nullable = "YES".equalsIgnoreCase(nullableStr);
                String defaultValue = rs.getString("COLUMN_DEF");
                String remarks = rs.getString("REMARKS");
                boolean isPk = primaryKeys.contains(columnName);

                columns.add(
                        new PhysicalColumnInfo(
                                columnName,
                                typeName,
                                columnSize,
                                nullable,
                                defaultValue,
                                isPk,
                                remarks));
            }
        }

        return columns;
    }

    public static class SchemaReadException extends RuntimeException {
        public SchemaReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record PhysicalTablePage(
            List<PhysicalTableInfo> items, long total, int pageNumber, int pageSize) {}
}

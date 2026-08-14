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

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.dto.datasource.PhysicalColumnInfo;
import io.github.malonetalk.dto.datasource.PhysicalTableInfo;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.BusinessException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            throw BusinessException.of(
                    ErrorCode.SCHEMA_READ_FAILED,
                    ErrorCode.SCHEMA_READ_FAILED.getDefaultMessage(),
                    e);
        }
    }

    public List<PhysicalColumnInfo> getTableSchema(Datasource datasource, String tableName) {
        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            String metadataTableName = resolveMetadataTableName(conn, tableName);
            Set<String> primaryKeys = getPrimaryKeys(conn, metadataTableName);
            return getColumns(conn, metadataTableName, primaryKeys);
        } catch (SQLException e) {
            log.error("Failed to read schema for table {}: {}", tableName, e.getMessage(), e);
            throw BusinessException.of(
                    ErrorCode.SCHEMA_READ_FAILED,
                    ErrorCode.SCHEMA_READ_FAILED.getDefaultMessage(),
                    e);
        }
    }

    public Map<String, Set<String>> getTableColumnNames(
            Datasource datasource, Collection<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> columnNamesByTable = new LinkedHashMap<>();
        Map<String, String> tableNameLookup = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            columnNamesByTable.putIfAbsent(tableName, new LinkedHashSet<>());
            tableNameLookup.putIfAbsent(caseInsensitiveKey(tableName), tableName);
        }

        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs =
                    metaData.getColumns(conn.getCatalog(), conn.getSchema(), "%", null)) {
                while (rs.next()) {
                    String metadataTableName = rs.getString("TABLE_NAME");
                    String tableName =
                            columnNamesByTable.containsKey(metadataTableName)
                                    ? metadataTableName
                                    : tableNameLookup.get(caseInsensitiveKey(metadataTableName));
                    Set<String> columnNames = columnNamesByTable.get(tableName);
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
                        conn.getCatalog(), conn.getSchema(), "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                tables.add(
                        new PhysicalTableInfo(rs.getString("TABLE_NAME"), rs.getString("REMARKS")));
            }
        }

        return tables;
    }

    private String resolveMetadataTableName(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        String[] types = new String[] {"TABLE"};

        try (ResultSet rs = metaData.getTables(catalog, schema, tableName, types)) {
            if (rs.next()) {
                return rs.getString("TABLE_NAME");
            }
        }
        try (ResultSet rs = metaData.getTables(catalog, schema, "%", types)) {
            while (rs.next()) {
                String metadataTableName = rs.getString("TABLE_NAME");
                if (metadataTableName != null && metadataTableName.equalsIgnoreCase(tableName)) {
                    return metadataTableName;
                }
            }
        }
        return tableName;
    }

    private String caseInsensitiveKey(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
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
}

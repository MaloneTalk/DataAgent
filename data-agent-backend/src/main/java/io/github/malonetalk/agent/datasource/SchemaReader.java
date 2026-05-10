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

import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.TableInfo;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SchemaReader {

    private static final Logger logger = LoggerFactory.getLogger(SchemaReader.class);

    private final DynamicDataSourceManager dynamicDataSourceManager;

    public SchemaReader(DynamicDataSourceManager dynamicDataSourceManager) {
        this.dynamicDataSourceManager = dynamicDataSourceManager;
    }

    public List<ColumnInfo> getTableSchema(Datasource datasource, String tableName) {
        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            Set<String> primaryKeys = getPrimaryKeys(conn, tableName);
            return getColumns(conn, tableName, primaryKeys);
        } catch (SQLException e) {
            logger.error("Failed to read schema for table {}: {}", tableName, e.getMessage(), e);
            throw new SchemaReadException(
                    "Failed to read schema for table " + tableName + ": " + e.getMessage(), e);
        }
    }

    public List<TableInfo> getTables(Datasource datasource) {
        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            return getTables(conn, datasource);
        } catch (SQLException e) {
            logger.error(
                    "Failed to read tables for datasource {}: {}",
                    datasource.getId(),
                    e.getMessage(),
                    e);
            throw new SchemaReadException(
                    "Failed to read tables for datasource "
                            + datasource.getId()
                            + ": "
                            + e.getMessage(),
                    e);
        }
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

    private List<ColumnInfo> getColumns(Connection conn, String tableName, Set<String> primaryKeys)
            throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
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
                        new ColumnInfo(
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

    private List<TableInfo> getTables(Connection conn, Datasource datasource) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs =
                metaData.getTables(
                        conn.getCatalog(), conn.getSchema(), "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                TableInfo tableInfo = new TableInfo();
                tableInfo.setTableName(rs.getString("TABLE_NAME"));
                tableInfo.setTableDescription(rs.getString("REMARKS"));
                tableInfo.setDatasourceId(datasource.getId());
                tableInfo.setIsActive(true);
                tableInfo.setIsVisible(true);
                tables.add(tableInfo);
            }
        }

        return tables;
    }

    public static class SchemaReadException extends RuntimeException {
        public SchemaReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

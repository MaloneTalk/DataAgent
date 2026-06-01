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
package io.github.malonetalk.infrastructure;

import io.github.malonetalk.entity.Column;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.PhysicalTable;
import io.github.malonetalk.entity.TableRelationInfo;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

    public List<Column> getTableSchema(Datasource datasource, String tableName) {
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

    public List<PhysicalTable> getTables(Datasource datasource) {
        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            List<PhysicalTable> tables = new ArrayList<>();
            try (ResultSet rs =
                    metaData.getTables(
                            conn.getCatalog(), conn.getSchema(), null, new String[] {"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName == null || tableName.isBlank()) {
                        continue;
                    }
                    PhysicalTable tableInfo = new PhysicalTable();
                    tableInfo.setTableName(tableName);
                    tableInfo.setTableDescription(normalizeBlankToNull(rs.getString("REMARKS")));
                    tableInfo.setDatasourceId(datasource.getId());
                    tables.add(tableInfo);
                }
            }
            return tables;
        } catch (SQLException e) {
            log.error(
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

    public List<TableRelationInfo> getImportedRelations(Datasource datasource, String tableName) {
        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            Map<String, TableRelationInfo> relationMap = new LinkedHashMap<>();
            try (ResultSet rs =
                    metaData.getImportedKeys(conn.getCatalog(), conn.getSchema(), tableName)) {
                while (rs.next()) {
                    String sourceTable = rs.getString("FKTABLE_NAME");
                    String sourceColumn = rs.getString("FKCOLUMN_NAME");
                    String targetTable = rs.getString("PKTABLE_NAME");
                    String targetColumn = rs.getString("PKCOLUMN_NAME");
                    if (anyBlank(sourceTable, sourceColumn, targetTable, targetColumn)) {
                        continue;
                    }
                    String key =
                            buildRelationKey(
                                    sourceTable,
                                    sourceColumn,
                                    targetTable,
                                    targetColumn,
                                    normalizeBlankToNull(rs.getString("FK_NAME")));
                    TableRelationInfo existing = relationMap.get(key);
                    if (existing != null) {
                        List<String> mergedSource =
                                mergeList(existing.sourceColumnNames(), sourceColumn);
                        List<String> mergedTarget =
                                mergeList(existing.targetColumnNames(), targetColumn);
                        relationMap.put(
                                key,
                                new TableRelationInfo(
                                        sourceTable,
                                        mergedSource,
                                        targetTable,
                                        mergedTarget,
                                        "foreign_key",
                                        null));
                    } else {
                        relationMap.put(
                                key,
                                new TableRelationInfo(
                                        sourceTable,
                                        List.of(sourceColumn),
                                        targetTable,
                                        List.of(targetColumn),
                                        "foreign_key",
                                        null));
                    }
                }
            }
            return List.copyOf(relationMap.values());
        } catch (SQLException e) {
            log.error(
                    "Failed to read imported relations for table {} in datasource {}: {}",
                    tableName,
                    datasource.getId(),
                    e.getMessage(),
                    e);
            throw new SchemaReadException(
                    "Failed to read imported relations for table "
                            + tableName
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

    private List<Column> getColumns(Connection conn, String tableName, Set<String> primaryKeys)
            throws SQLException {
        List<Column> columns = new ArrayList<>();
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
                        new Column(
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

    private static String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean anyBlank(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static String buildRelationKey(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String foreignKeyName) {
        if (foreignKeyName != null) {
            return sourceTable + "|" + targetTable + "|" + foreignKeyName;
        }
        return sourceTable + "|" + sourceColumn + "|" + targetTable + "|" + targetColumn;
    }

    private static List<String> mergeList(List<String> existing, String newValue) {
        List<String> merged = new ArrayList<>(existing);
        merged.add(newValue);
        return merged;
    }

    public static class SchemaReadException extends RuntimeException {
        public SchemaReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

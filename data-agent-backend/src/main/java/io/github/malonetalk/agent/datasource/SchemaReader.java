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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SchemaReader {

    private static final Logger logger = LoggerFactory.getLogger(SchemaReader.class);
    private static final long CACHE_TTL_MILLIS = 60_000L;

    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final Map<Integer, CacheEntry<List<TableInfo>>> tableCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<ColumnInfo>>> columnCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<TableRelationInfo>>> relationCache =
            new ConcurrentHashMap<>();

    public SchemaReader(DynamicDataSourceManager dynamicDataSourceManager) {
        this.dynamicDataSourceManager = dynamicDataSourceManager;
    }

    public void invalidateCache(Integer datasourceId) {
        tableCache.remove(datasourceId);
        columnCache.keySet().removeIf(key -> key.startsWith(datasourceId + ":"));
        relationCache.keySet().removeIf(key -> key.startsWith(datasourceId + ":"));
    }

    public List<ColumnInfo> getTableSchema(Datasource datasource, String tableName) {
        String cacheKey = buildTableCacheKey(datasource.getId(), tableName);
        List<ColumnInfo> cachedColumns = getCachedValue(columnCache, cacheKey);
        if (cachedColumns != null) {
            return cachedColumns;
        }

        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);
        try (Connection conn = ds.getConnection()) {
            Set<String> primaryKeys = getPrimaryKeys(conn, tableName);
            List<ColumnInfo> columns = List.copyOf(getColumns(conn, tableName, primaryKeys));
            putCachedValue(columnCache, cacheKey, columns);
            return columns;
        } catch (SQLException e) {
            logger.error("Failed to read schema for table {}: {}", tableName, e.getMessage(), e);
            throw new SchemaReadException(
                    "Failed to read schema for table " + tableName + ": " + e.getMessage(), e);
        }
    }

    public List<TableInfo> getTables(Datasource datasource) {
        List<TableInfo> cachedTables = getCachedValue(tableCache, datasource.getId());
        if (cachedTables != null) {
            return copyTableInfos(cachedTables);
        }

        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);
        try (Connection conn = ds.getConnection()) {
            List<TableInfo> tables = getTables(conn, datasource);
            putCachedValue(tableCache, datasource.getId(), copyTableInfos(tables));
            return tables;
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

    public List<TableRelationInfo> getImportedRelations(Datasource datasource, String tableName) {
        String cacheKey = buildTableCacheKey(datasource.getId(), tableName);
        List<TableRelationInfo> cachedRelations = getCachedValue(relationCache, cacheKey);
        if (cachedRelations != null) {
            return cachedRelations;
        }

        javax.sql.DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);
        try (Connection conn = ds.getConnection()) {
            List<TableRelationInfo> relations = List.copyOf(getImportedRelations(conn, tableName));
            putCachedValue(relationCache, cacheKey, relations);
            return relations;
        } catch (SQLException e) {
            logger.error(
                    "Failed to read imported keys for table {}: {}", tableName, e.getMessage(), e);
            throw new SchemaReadException(
                    "Failed to read imported keys for table " + tableName + ": " + e.getMessage(),
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
                tableInfo.setIsVisible(true);
                tables.add(tableInfo);
            }
        }

        return tables;
    }

    private List<TableRelationInfo> getImportedRelations(Connection conn, String tableName)
            throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        Map<String, ImportedKeyAggregate> aggregates = new LinkedHashMap<>();
        int unnamedRelationSequence = 0;
        String previousUnnamedKey = null;

        try (ResultSet rs =
                metaData.getImportedKeys(conn.getCatalog(), conn.getSchema(), tableName)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String fkTableName = rs.getString("FKTABLE_NAME");
                String pkTableName = rs.getString("PKTABLE_NAME");
                short keySeq = rs.getShort("KEY_SEQ");
                String aggregateKey;
                if (fkName != null && !fkName.isBlank()) {
                    aggregateKey = "named:" + fkName.trim();
                } else {
                    if (keySeq <= 1 || previousUnnamedKey == null) {
                        unnamedRelationSequence++;
                        previousUnnamedKey =
                                "unnamed:"
                                        + Objects.toString(fkTableName, "")
                                        + "->"
                                        + Objects.toString(pkTableName, "")
                                        + "#"
                                        + unnamedRelationSequence;
                    }
                    aggregateKey = previousUnnamedKey;
                }

                ImportedKeyAggregate aggregate =
                        aggregates.computeIfAbsent(
                                aggregateKey,
                                key ->
                                        new ImportedKeyAggregate(
                                                fkTableName,
                                                pkTableName,
                                                "foreign_key",
                                                buildPhysicalRelationDescription(fkName)));
                aggregate.addColumnPair(
                        keySeq, rs.getString("FKCOLUMN_NAME"), rs.getString("PKCOLUMN_NAME"));
            }
        }

        return aggregates.values().stream().map(ImportedKeyAggregate::toRelationInfo).toList();
    }

    private String buildPhysicalRelationDescription(String fkName) {
        if (fkName == null || fkName.isBlank()) {
            return "Physical foreign key discovered from JDBC metadata.";
        }
        return "Physical foreign key discovered from JDBC metadata: " + fkName.trim();
    }

    private List<TableInfo> copyTableInfos(List<TableInfo> tables) {
        return tables.stream().map(this::copyTableInfo).toList();
    }

    private TableInfo copyTableInfo(TableInfo source) {
        TableInfo target = new TableInfo();
        target.setId(source.getId());
        target.setTableName(source.getTableName());
        target.setTableDescription(source.getTableDescription());
        target.setDomain(source.getDomain());
        target.setDatasourceId(source.getDatasourceId());
        target.setIsVisible(source.getIsVisible());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    private String buildTableCacheKey(Integer datasourceId, String tableName) {
        return datasourceId + ":" + tableName;
    }

    private <K, V> V getCachedValue(Map<K, CacheEntry<V>> cache, K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAtMillis() <= System.currentTimeMillis()) {
            cache.remove(key);
            return null;
        }
        return entry.value();
    }

    private <K, V> void putCachedValue(Map<K, CacheEntry<V>> cache, K key, V value) {
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + CACHE_TTL_MILLIS));
    }

    private record CacheEntry<T>(T value, long expireAtMillis) {}

    private static class ImportedKeyAggregate {
        private final String sourceTableName;
        private final String targetTableName;
        private final String relationType;
        private final String description;
        private final List<ImportedKeyColumnPair> columnPairs = new ArrayList<>();

        private ImportedKeyAggregate(
                String sourceTableName,
                String targetTableName,
                String relationType,
                String description) {
            this.sourceTableName = sourceTableName;
            this.targetTableName = targetTableName;
            this.relationType = relationType;
            this.description = description;
        }

        private void addColumnPair(short keySeq, String sourceColumnName, String targetColumnName) {
            columnPairs.add(new ImportedKeyColumnPair(keySeq, sourceColumnName, targetColumnName));
        }

        private TableRelationInfo toRelationInfo() {
            List<ImportedKeyColumnPair> sortedPairs =
                    columnPairs.stream()
                            .sorted(Comparator.comparingInt(ImportedKeyColumnPair::keySeq))
                            .toList();
            List<String> sourceColumnNames =
                    sortedPairs.stream().map(ImportedKeyColumnPair::sourceColumnName).toList();
            List<String> targetColumnNames =
                    sortedPairs.stream().map(ImportedKeyColumnPair::targetColumnName).toList();
            return new TableRelationInfo(
                    sourceTableName,
                    sourceColumnNames,
                    targetTableName,
                    targetColumnNames,
                    relationType,
                    description);
        }
    }

    private record ImportedKeyColumnPair(
            short keySeq, String sourceColumnName, String targetColumnName) {}

    public static class SchemaReadException extends RuntimeException {
        public SchemaReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

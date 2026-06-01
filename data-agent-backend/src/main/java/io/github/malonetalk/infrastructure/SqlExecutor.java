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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import io.github.malonetalk.common.QueryResult;
import io.github.malonetalk.entity.Datasource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class SqlExecutor {

    private static final int MAX_ROWS = 200;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private static final Pattern SELECT_PATTERN =
            Pattern.compile("^\\s*SELECT\\s", Pattern.CASE_INSENSITIVE);

    private static final Pattern FORBIDDEN_PATTERN =
            Pattern.compile(
                    ";\\s*(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|GRANT|REVOKE)\\s",
                    Pattern.CASE_INSENSITIVE);

    private final DynamicDataSourceManager dynamicDataSourceManager;

    public QueryResult execute(Datasource datasource, String sql) {
        validateSql(sql);

        DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            return doExecute(conn, sql);
        } catch (SQLException e) {
            log.error("SQL execution failed: {}", e.getMessage(), e);
            throw new SqlExecutionException("SQL execution failed: " + e.getMessage(), e);
        }
    }

    private void validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL must not be empty");
        }

        if (!SELECT_PATTERN.matcher(sql).find()) {
            throw new SqlSecurityException("Only SELECT queries are allowed. Received: " + sql);
        }

        if (FORBIDDEN_PATTERN.matcher(sql).find()) {
            throw new SqlSecurityException(
                    "SQL contains forbidden statements. Only SELECT is allowed.");
        }
    }

    private QueryResult doExecute(Connection conn, String sql) throws SQLException {
        try (PreparedStatement stmt =
                conn.prepareStatement(
                        sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            stmt.setFetchSize(MAX_ROWS);

            try (ResultSet rs = stmt.executeQuery()) {
                return mapResultSet(rs);
            }
        }
    }

    private QueryResult mapResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        QueryResult.Builder builder = QueryResult.builder();
        for (int i = 1; i <= columnCount; i++) {
            builder.addColumn(metaData.getColumnLabel(i));
        }

        int rowCount = 0;
        boolean truncated = false;

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnLabel(i), rs.getObject(i));
            }
            builder.addRow(row);
            rowCount++;

            if (rowCount >= MAX_ROWS) {
                truncated = true;
                break;
            }
        }

        builder.totalRows(rowCount).truncated(truncated);
        return builder.build();
    }

    public static class SqlExecutionException extends RuntimeException {
        public SqlExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class SqlSecurityException extends RuntimeException {
        public SqlSecurityException(String message) {
            super(message);
        }
    }
}

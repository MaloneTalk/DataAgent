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
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.BusinessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class SqlExecutor {

    static final int MAX_ROWS = 200;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private final DynamicDataSourceManager dynamicDataSourceManager;

    public QueryResult execute(Datasource datasource, String sql) {
        String validatedSql = validateAndTransform(sql);

        DataSource ds = dynamicDataSourceManager.getOrCreateDataSource(datasource);

        try (Connection conn = ds.getConnection()) {
            return doExecute(conn, validatedSql);
        } catch (SQLException e) {
            log.error("SQL execution failed: {}", e.getMessage(), e);
            throw BusinessException.of(
                    ErrorCode.SQL_EXECUTION_FAILED,
                    ErrorCode.SQL_EXECUTION_FAILED.getDefaultMessage(),
                    e);
        }
    }

    String validateAndTransform(String sql) {
        if (sql == null || sql.isBlank()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "SQL must not be empty.");
        }

        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw BusinessException.of(
                    ErrorCode.SQL_NOT_ALLOWED, "Invalid SQL syntax: " + e.getMessage(), e);
        }

        if (!(stmt instanceof Select select)) {
            throw BusinessException.of(
                    ErrorCode.SQL_NOT_ALLOWED,
                    "Only SELECT queries are allowed. Got: "
                            + stmt.getClass().getSimpleName()
                            + ". SQL: "
                            + sql);
        }

        // Block SELECT ... INTO (MySQL OUTFILE / DUMPFILE / table)
        PlainSelect ps = select.getPlainSelect();
        if (ps != null && ps.getIntoTables() != null) {
            throw BusinessException.of(ErrorCode.SQL_NOT_ALLOWED, "SELECT INTO is not allowed.");
        }

        //  inject LIMIT if absent, to prevent full table scans
        if (!hasLimit(select)) {
            // strip trailing semicolon to avoid syntax error in subquery
            String safeSql = sql.trim();
            if (safeSql.endsWith(";")) {
                safeSql = safeSql.substring(0, safeSql.length() - 1).trim();
            }
            return "SELECT * FROM (" + safeSql + ") AS _sandbox LIMIT " + MAX_ROWS;
        }
        return sql;
    }

    private boolean hasLimit(Select select) {
        return select.getLimit() != null || select.getFetch() != null;
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
}

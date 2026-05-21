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
package io.github.malonetalk.agent.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.agent.datasource.SqlExecutor;
import io.github.malonetalk.agent.datasource.SqlExecutor.SqlExecutionException;
import io.github.malonetalk.agent.datasource.SqlExecutor.SqlSecurityException;
import io.github.malonetalk.common.ToolResult;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.service.ActiveDatasourceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExecuteSqlTool implements MarkAgentTool {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteSqlTool.class);

    private final ActiveDatasourceSupport activeDatasourceSupport;
    private final SqlExecutor sqlExecutor;

    public ExecuteSqlTool(
            ActiveDatasourceSupport activeDatasourceSupport, SqlExecutor sqlExecutor) {
        this.activeDatasourceSupport = activeDatasourceSupport;
        this.sqlExecutor = sqlExecutor;
    }

    @Tool(
            name = "execute_sql",
            description =
                    "Execute a SELECT SQL query on the target datasource and return structured"
                            + " result data. Returns a success/data/error wrapper. Only supports"
                            + " SELECT queries, does not support INSERT/UPDATE/DELETE or other"
                            + " modification operations.")
    public ToolResult<QueryResult> executeSql(
            @ToolParam(name = "sql", description = "The SELECT SQL query statement to execute")
                    String sql) {
        Datasource datasource = activeDatasourceSupport.getActiveDatasource();
        if (datasource == null) {
            return ToolResult.error(
                    "NO_ACTIVE_DATASOURCE", "No active datasource available, cannot execute SQL.");
        }

        try {
            return ToolResult.success(sqlExecutor.execute(datasource, sql));
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_SQL_ARGUMENT", e.getMessage());
        } catch (SqlSecurityException e) {
            return ToolResult.error("SQL_SECURITY_ERROR", e.getMessage());
        } catch (SqlExecutionException e) {
            return ToolResult.error("SQL_EXECUTION_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Unexpected SQL tool failure: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTE_SQL_INTERNAL_ERROR", e.getMessage());
        }
    }
}

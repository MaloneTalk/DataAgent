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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.agent.datasource.SqlExecutor;
import io.github.malonetalk.agent.datasource.SqlExecutor.SqlExecution;
import io.github.malonetalk.model.bo.SqlToolMetadataBo;
import io.github.malonetalk.model.bo.ToolResultBo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.DatasourceService;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ExecuteSqlTool implements MarkAgentTool {

    private final DatasourceService dataSourceService;
    private final SqlExecutor sqlExecutor;
    private final ToolExceptionMapper toolExceptionMapper;
    private final ObjectMapper objectMapper;

    @Tool(
            name = "execute_sql",
            converter = ToolResultBoConverter.class,
            description =
                    "Execute SELECT SQL query on the target datasource and return the query result."
                        + " Only supports SELECT queries, does not support INSERT/UPDATE/DELETE or"
                        + " other modification operations.")
    public ToolResultBo executeSql(
            @ToolParam(name = "sql", description = "The SELECT SQL query statement to execute")
                    String sql,
            ToolCallContext ctx) {
        return toolExceptionMapper.runResult(
                () -> {
                    DatasourcePo datasource =
                            dataSourceService.getDatasourceForSession(ctx.sessionId());
                    SqlExecution execution =
                            sqlExecutor.executeDetailed(datasource, sql, ctx.userId());
                    var metadata =
                            SqlToolMetadataBo.builder()
                                    .datasourceId(datasource.getId())
                                    .datasourceName(datasource.getName())
                                    .databaseName(datasource.getDatabaseName())
                                    .executedSql(execution.executedSql())
                                    .authorizedColumns(execution.authorizedColumns())
                                    .returnedRows(execution.result().rows().size())
                                    .truncated(execution.result().truncated())
                                    .build();
                    Map<String, Object> resultMetadata =
                            objectMapper.convertValue(metadata, new TypeReference<>() {});
                    return new ToolResultBo(formatResult(execution.result()), resultMetadata);
                });
    }

    private String formatResult(QueryResult result) {
        if (result.rows().isEmpty()) {
            return "Query result is empty.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Query result (total ").append(result.totalRows()).append(" rows)");
        if (result.truncated()) {
            sb.append(", truncated to show first ").append(result.rows().size()).append(" rows");
        }
        sb.append(":\n");

        sb.append("Columns: ").append(result.columns()).append("\n");

        for (int i = 0; i < result.rows().size(); i++) {
            sb.append("Row ").append(i + 1).append(": ").append(result.rows().get(i)).append("\n");
        }

        return sb.toString();
    }
}

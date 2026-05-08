package io.github.malonetalk.agent.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.agent.datasource.SqlExecutor;
import io.github.malonetalk.agent.datasource.SqlExecutor.SqlExecutionException;
import io.github.malonetalk.agent.datasource.SqlExecutor.SqlSecurityException;
import io.github.malonetalk.common.StatusConstants;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.service.DatasourceService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExecuteSqlTool {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteSqlTool.class);

    private final DatasourceService dataSourceService;
    private final SqlExecutor sqlExecutor;

    public ExecuteSqlTool(DatasourceService dataSourceService, SqlExecutor sqlExecutor) {
        this.dataSourceService = dataSourceService;
        this.sqlExecutor = sqlExecutor;
    }

    @Tool(
            name = "execute_sql",
            description =
                    "Execute SELECT SQL query on the target datasource and return the query result."
                        + " Only supports SELECT queries, does not support INSERT/UPDATE/DELETE or"
                        + " other modification operations.")
    public String executeSql(
            @ToolParam(name = "sql", description = "The SELECT SQL query statement to execute")
                    String sql) {
        List<Datasource> activeDataSources = dataSourceService.findByStatus(StatusConstants.ACTIVE);

        if (activeDataSources.isEmpty()) {
            return "No active datasource available, cannot execute SQL.";
        }

        if (activeDataSources.size() > 1) {
            logger.warn(
                    "Found {} active data sources, using the first one.", activeDataSources.size());
        }

        Datasource datasource = activeDataSources.get(0);

        try {
            QueryResult result = sqlExecutor.execute(datasource, sql);
            return formatResult(result);
        } catch (SqlSecurityException e) {
            return "SQL execution denied: " + e.getMessage();
        } catch (SqlExecutionException e) {
            return "SQL execution failed: " + e.getMessage();
        }
    }

    private String formatResult(QueryResult result) {
        if (result.getRows().isEmpty()) {
            return "Query result is empty.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Query result (total ").append(result.getTotalRows()).append(" rows)");
        if (result.isTruncated()) {
            sb.append(", truncated to show first ").append(result.getRows().size()).append(" rows");
        }
        sb.append(":\n");

        sb.append("Columns: ").append(result.getColumns()).append("\n");

        for (int i = 0; i < result.getRows().size(); i++) {
            sb.append("Row ")
                    .append(i + 1)
                    .append(": ")
                    .append(result.getRows().get(i))
                    .append("\n");
        }

        return sb.toString();
    }
}

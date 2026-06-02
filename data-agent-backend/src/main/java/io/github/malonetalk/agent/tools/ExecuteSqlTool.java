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
import io.github.malonetalk.common.QueryResult;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.infrastructure.SqlExecutor;
import io.github.malonetalk.infrastructure.SqlExecutor.SqlExecutionException;
import io.github.malonetalk.infrastructure.SqlExecutor.SqlSecurityException;
import io.github.malonetalk.service.DatasourceService;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class ExecuteSqlTool implements MarkAgentTool {

    private final DatasourceService dataSourceService;
    private final SqlExecutor sqlExecutor;

    @Tool(
            name = "execute_sql",
            description =
                    "在当前活跃数据源上执行 SELECT SQL 查询，并返回查询结果。"
                            + "仅支持 SELECT 查询，不支持 INSERT、UPDATE、DELETE 或其他修改数据的操作。")
    public String executeSql(
            @ToolParam(name = "sql", description = "要执行的 SELECT SQL 查询语句") String sql) {
        List<Datasource> activeDataSources =
                dataSourceService.findByStatus(Status.ACTIVE.getCode());

        if (activeDataSources.isEmpty()) {
            return "没有可用的活跃数据源，无法执行 SQL。";
        }

        if (activeDataSources.size() > 1) {
            log.warn(
                    "Found {} active data sources, using the first one.", activeDataSources.size());
        }

        Datasource datasource = activeDataSources.get(0);

        try {
            QueryResult result = sqlExecutor.execute(datasource, sql);
            return formatResult(result);
        } catch (SqlSecurityException e) {
            return "SQL 执行被拒绝：" + e.getMessage();
        } catch (SqlExecutionException e) {
            return "SQL 执行失败：" + e.getMessage();
        }
    }

    private String formatResult(QueryResult result) {
        if (result.rows().isEmpty()) {
            return "查询结果为空。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("查询结果（共 ").append(result.totalRows()).append(" 行）");
        if (result.truncated()) {
            sb.append("，结果已截断，仅展示前 ").append(result.rows().size()).append(" 行");
        }
        sb.append(":\n");

        sb.append("列：").append(result.columns()).append("\n");

        for (int i = 0; i < result.rows().size(); i++) {
            sb.append("第 ").append(i + 1).append(" 行：").append(result.rows().get(i)).append("\n");
        }

        return sb.toString();
    }
}

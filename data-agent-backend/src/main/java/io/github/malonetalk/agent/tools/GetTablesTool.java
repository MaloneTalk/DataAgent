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

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.util.JsonUtils;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.ToolExceptionMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.table.TableSemanticService;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GetTablesTool implements MarkAgentTool {

    private final DatasourceService dataSourceService;
    private final TableSemanticService tableSemanticService;
    private final ToolExceptionMapper toolExceptionMapper;

    @Tool(
            name = "get_tables",
            description =
                    """
                    Get synced semantic-layer table information, including table name, domain, \
                    description and enabled relations.\
                    """)
    public ToolResultBlock getTables(
            @ToolParam(
                            name = "domains",
                            description =
                                    """
                                    Optional list of domain names. Only tables belonging to these \
                                    domains will be returned. If not provided or empty, \
                                    returns all tables.\
                                    """,
                            required = false)
                    List<String> domains,
            ToolCallContext ctx) {
        return toolExceptionMapper.run(
                () -> {
                    Datasource dataSource =
                            dataSourceService.getDatasourceForSession(ctx.sessionId());
                    return ToolResultBlock.text(
                            JsonUtils.getJsonCodec()
                                            .toJson(
                                                    tableSemanticService.listMergedTablesByDomains(
                                                            dataSource.getId(), domains))
                                    + ToolCallConstants.METRIC_CALIBER_REMINDER);
                });
    }
}

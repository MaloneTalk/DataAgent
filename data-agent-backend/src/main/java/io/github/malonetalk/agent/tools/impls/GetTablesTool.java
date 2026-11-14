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
package io.github.malonetalk.agent.tools.impls;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.util.JsonUtils;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.agent.tools.DataAgentTool;
import io.github.malonetalk.agent.tools.RestTool;
import io.github.malonetalk.agent.tools.ToolCallConstants;
import io.github.malonetalk.agent.tools.ToolDatasourceResolver;
import io.github.malonetalk.agent.tools.ToolExceptionMapper;
import io.github.malonetalk.agent.tools.ToolResultBoConverter;
import io.github.malonetalk.dto.prompt.TablePromptResponse;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.model.bo.DataPermissionScopeBo;
import io.github.malonetalk.model.bo.ToolResultBo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.PermissionService;
import io.github.malonetalk.service.semantic.table.TableSemanticService;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GetTablesTool implements DataAgentTool {

    private final PermissionService permissions;

    private final ToolDatasourceResolver datasourceResolver;
    private final DataPermissionService dataPermissions;
    private final TableSemanticService tableSemanticService;
    private final ToolExceptionMapper toolExceptionMapper;

    @RestTool
    @Tool(
            name = "get_tables",
            converter = ToolResultBoConverter.class,
            description =
                    """
                    Get synced semantic-layer tables and ordinary views, including name, object type, domain, \
                    description, data granularity (what one row represents) and enabled relations.\
                    """)
    public ToolResultBo getTables(
            @ToolParam(
                            name = "datasource_id",
                            description =
                                    "Datasource id. Optional only when exactly one datasource is"
                                            + " available.",
                            required = false)
                    Integer datasourceId,
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
        return toolExceptionMapper.runResult(
                () -> {
                    var user = permissions.require(ctx.userId(), PermissionEnum.DATA_SCHEMA_READ);
                    DatasourcePo dataSource = datasourceResolver.resolve(ctx, datasourceId);
                    var scope = dataPermissions.loadScope(user, dataSource.getId());

                    var visible =
                            tableSemanticService
                                    .listMergedTablesByDomains(dataSource.getId(), domains)
                                    .stream()
                                    .filter(t -> scope.canTable(t.name()))
                                    .map(t -> filterTable(t, scope))
                                    .toList();
                    return new ToolResultBo(
                            JsonUtils.getJsonCodec().toJson(visible)
                                    + ToolCallConstants.METRIC_CALIBER_REMINDER,
                            Map.of(
                                    "datasourceId", dataSource.getId(),
                                    "datasourceName", dataSource.getName()));
                });
    }

    private TablePromptResponse filterTable(
            TablePromptResponse table, DataPermissionScopeBo scope) {
        var relations =
                table.relations().stream()
                        .filter(
                                rel ->
                                        scope.canColumns(
                                                        rel.sourceTableName(),
                                                        rel.sourceColumnNames())
                                                && scope.canColumns(
                                                        rel.targetTableName(),
                                                        rel.targetColumnNames()))
                        .toList();
        return table.toBuilder().relations(relations).build();
    }
}

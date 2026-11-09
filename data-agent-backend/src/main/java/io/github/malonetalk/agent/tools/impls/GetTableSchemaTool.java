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
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.agent.tools.DataAgentTool;
import io.github.malonetalk.agent.tools.RestTool;
import io.github.malonetalk.agent.tools.ToolCallConstants;
import io.github.malonetalk.agent.tools.ToolDatasourceResolver;
import io.github.malonetalk.agent.tools.ToolExceptionMapper;
import io.github.malonetalk.agent.tools.ToolResultBoConverter;
import io.github.malonetalk.dto.prompt.ColumnPromptResponse;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.model.bo.ToolResultBo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.PermissionService;
import io.github.malonetalk.service.semantic.column.ColumnSemanticService;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GetTableSchemaTool implements DataAgentTool {

    private final PermissionService permissions;

    private final ToolDatasourceResolver datasourceResolver;
    private final DataPermissionService dataPermissions;
    private final ColumnSemanticService columnSemanticService;
    private final ToolExceptionMapper toolExceptionMapper;

    @RestTool
    @Tool(
            name = "get_table_schema",
            converter = ToolResultBoConverter.class,
            description =
                    """
                    Get synced semantic-layer schema information for the specified table, \
                    including column name, physical data type, semantic type, primary key flag, \
                    index hints and column descriptions. Call this tool before generating SQL.\
                    """)
    public ToolResultBo getTableSchema(
            @ToolParam(
                            name = "datasource_id",
                            description =
                                    "Datasource id. Optional only when exactly one datasource is"
                                            + " available.",
                            required = false)
                    Integer datasourceId,
            @ToolParam(name = "table_name", description = "The table name to query schema for")
                    String tableName,
            ToolCallContext ctx) {
        return toolExceptionMapper.runResult(
                () -> {
                    var user = permissions.require(ctx.userId(), PermissionEnum.DATA_SCHEMA_READ);
                    DatasourcePo datasource = datasourceResolver.resolve(ctx, datasourceId);

                    var scope = dataPermissions.loadScope(user, datasource.getId());
                    String authorizedTable = scope.requireTable(tableName);
                    List<ColumnPromptResponse> columns =
                            columnSemanticService
                                    .getMergedTableSchema(datasource.getId(), authorizedTable)
                                    .stream()
                                    .filter(c -> scope.canColumn(authorizedTable, c.name()))
                                    .toList();
                    return new ToolResultBo(
                            SemanticUtils.formatTableSchema(authorizedTable, columns)
                                    + ToolCallConstants.METRIC_CALIBER_REMINDER,
                            Map.of(
                                    "datasourceId", datasource.getId(),
                                    "datasourceName", datasource.getName()));
                });
    }
}

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
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.dto.prompt.ColumnPromptResponse;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.exception.ToolExceptionMapper;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.PermissionService;
import io.github.malonetalk.service.semantic.column.ColumnSemanticService;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GetTableSchemaTool implements MarkAgentTool {

    private final PermissionService permissions;

    private final DatasourceService dataSourceService;
    private final DataPermissionService dataPermissions;
    private final ColumnSemanticService columnSemanticService;
    private final ToolExceptionMapper toolExceptionMapper;

    @Tool(
            name = "get_table_schema",
            description =
                    """
                    Get synced semantic-layer schema information for the specified table, \
                    including column name, physical data type, semantic type, primary key flag, \
                    index hints and column descriptions. Call this tool before generating SQL.\
                    """)
    public ToolResultBlock getTableSchema(
            @ToolParam(name = "table_name", description = "The table name to query schema for")
                    String tableName,
            ToolCallContext ctx) {
        var user = permissions.require(ctx.userId(), PermissionEnum.DATA_SCHEMA_READ);
        return toolExceptionMapper.run(
                () -> {
                    Datasource datasource =
                            dataSourceService.getDatasourceForSession(ctx.sessionId());

                    dataPermissions.requireTable(user, datasource.getId(), tableName);
                    var hidden = dataPermissions.hidden(user, datasource.getId(), tableName);
                    List<ColumnPromptResponse> columns =
                            columnSemanticService
                                    .getMergedTableSchema(datasource.getId(), tableName)
                                    .stream()
                                    .filter(
                                            c ->
                                                    !hidden.contains(
                                                            SemanticUtils.normalizeIdentifier(
                                                                    c.name())))
                                    .toList();
                    return ToolResultBlock.text(
                            SemanticUtils.formatTableSchema(tableName, columns)
                                    + ToolCallConstants.METRIC_CALIBER_REMINDER);
                });
    }
}

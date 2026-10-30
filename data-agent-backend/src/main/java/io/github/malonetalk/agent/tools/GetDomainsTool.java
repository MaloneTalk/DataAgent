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
import io.agentscope.core.util.JsonUtils;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.exception.ToolExceptionMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.PermissionService;
import io.github.malonetalk.service.semantic.DomainService;
import io.github.malonetalk.service.semantic.SemanticAvailabilityHelper;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GetDomainsTool implements MarkAgentTool {

    private final PermissionService permissions;

    private final DomainService domainService;
    private final DatasourceService datasources;
    private final TableInfoMapper tableMetadata;
    private final DataPermissionService dataPermissions;
    private final ToolExceptionMapper toolExceptionMapper;

    @Tool(
            name = "get_domains",
            description =
                    "Get available data domains, including domain names and descriptions. Call"
                            + " this tool first to choose the most relevant domains before querying"
                            + " tables.")
    public ToolResultBlock getDomains(ToolCallContext ctx) {
        var user = permissions.require(ctx.userId(), PermissionEnum.DATA_SCHEMA_READ);
        return toolExceptionMapper.run(
                () -> {
                    var datasource = datasources.getDatasourceForSession(ctx.sessionId());
                    var scope = dataPermissions.loadScope(user, datasource.getId());
                    var domains =
                            tableMetadata.selectByDatasourceId(datasource.getId()).stream()
                                    .filter(SemanticAvailabilityHelper::isTableAvailable)
                                    .filter(table -> scope.canTable(table.getTableName()))
                                    .map(table -> SemanticUtils.normalizeDomain(table.getDomain()))
                                    .collect(Collectors.toSet());
                    return ToolResultBlock.text(
                            JsonUtils.getJsonCodec()
                                    .toJson(
                                            domainService.listDomainPrompts().stream()
                                                    .filter(
                                                            domain ->
                                                                    domains.contains(domain.name()))
                                                    .toList()));
                });
    }
}

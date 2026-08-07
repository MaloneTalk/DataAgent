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
import io.github.malonetalk.exception.ToolExceptionMapper;
import io.github.malonetalk.service.semantic.DomainService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GetDomainsTool implements MarkAgentTool {

    private final DomainService domainService;
    private final ToolExceptionMapper toolExceptionMapper;

    @Tool(
            name = "get_domains",
            description =
                    "Get available data domains in the datasource. Call this tool first to discover"
                            + " what domains are available before querying tables.")
    public ToolResultBlock getDomains() {
        return toolExceptionMapper.run(
                () ->
                        ToolResultBlock.text(
                                JsonUtils.getJsonCodec().toJson(domainService.listDomainNames())));
    }
}

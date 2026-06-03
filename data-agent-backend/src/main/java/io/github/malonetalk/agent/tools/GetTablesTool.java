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
import io.github.malonetalk.agent.tools.response.TablePromptResponse;
import io.github.malonetalk.common.ToolResult;
import io.github.malonetalk.dto.PageResponse;
import io.github.malonetalk.service.semantic.SemanticMergeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetTablesTool implements MarkAgentTool {

    private final SemanticMergeService semanticMergeService;

    @Tool(name = "get_tables", description = "获取数据库中的表信息，包括表名和表描述")
    public ToolResult<PageResponse<TablePromptResponse>> getTables(
            @ToolParam(name = "page", description = "Optional page number, default is 1") Integer page,
            @ToolParam(name = "page_size", description = "Optional page size, default is 20, maximum is 100") Integer pageSize) {
        try {
            int resolvedPage = PageResponse.resolvePage(page);
            int resolvedPageSize = PageResponse.resolvePageSize(pageSize);
            return ToolResult.success(
                    semanticMergeService.getVisibleTablePromptPage(resolvedPage, resolvedPageSize));
        } catch (IllegalStateException e) {
            return ToolResult.error("Data source parsing failed", e.getMessage());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid arguments", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Failed to retrieve visible tables: {}", e.getMessage(), e);
            return ToolResult.error("Failed to retrieve tables", e.getMessage());
        }
    }
}

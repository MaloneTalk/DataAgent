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

    @Tool(
            name = "get_tables",
            description =
                    "获取可见的表信息，包含表名、领域、描述以及该表与其他表的逻辑关系。"
                            + "返回 success/data/error 包装结构，data 包含分页的表项。")
    public ToolResult<PageResponse<TablePromptResponse>> getTables(
            @ToolParam(name = "page", description = "可选页码，默认为1") Integer page,
            @ToolParam(name = "page_size", description = "可选每页大小，默认20，最大100") Integer pageSize) {
        try {
            int resolvedPage = PageResponse.resolvePage(page);
            int resolvedPageSize = PageResponse.resolvePageSize(pageSize);
            return ToolResult.success(
                    semanticMergeService.getVisibleTablePromptPage(resolvedPage, resolvedPageSize));
        } catch (IllegalStateException e) {
            return ToolResult.error("数据源解析失败", e.getMessage());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("参数错误", e.getMessage());
        } catch (RuntimeException e) {
            log.error("获取可见表失败: {}", e.getMessage(), e);
            return ToolResult.error("获取表失败", e.getMessage());
        }
    }
}

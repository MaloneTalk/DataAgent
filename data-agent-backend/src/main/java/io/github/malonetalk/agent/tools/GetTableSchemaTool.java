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
import io.github.malonetalk.common.ToolResult;
import io.github.malonetalk.dto.PageResponse;
import io.github.malonetalk.dto.semantic.TableSchemaSemanticPrompt;
import io.github.malonetalk.service.semantic.SemanticMergeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetTableSchemaTool implements MarkAgentTool {

    private final SemanticMergeService semanticMergeService;

    @Tool(
            name = "get_table_schema",
            description =
                    "获取指定表的语义 Schema 信息，包含表元数据和分页可见列信息。"
                            + "语义描述优先于物理元数据作为兜底。"
                            + "表关系通过 get_tables 工具获取，不通过本工具返回。"
                            + "在生成 SQL 之前应调用此工具了解表结构。")
    public ToolResult<TableSchemaSemanticPrompt> getTableSchema(
            @ToolParam(name = "table_name", description = "要查询 Schema 的表名") String tableName,
            @ToolParam(name = "column_page", description = "可选列页码，默认为1") Integer columnPage,
            @ToolParam(name = "column_page_size", description = "可选列每页大小，默认20，最大100")
                    Integer columnPageSize) {
        try {
            int resolvedPage = PageResponse.resolvePage(columnPage);
            int resolvedPageSize = PageResponse.resolvePageSize(columnPageSize);
            return ToolResult.success(
                    semanticMergeService.getTableSchema(tableName, resolvedPage, resolvedPageSize));
        } catch (IllegalStateException e) {
            return ToolResult.error("数据源解析失败", e.getMessage());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("参数错误", e.getMessage());
        } catch (RuntimeException e) {
            log.error("获取表 {} 的 Schema 失败: {}", tableName, e.getMessage(), e);
            return ToolResult.error("获取 Schema 失败", e.getMessage());
        }
    }
}

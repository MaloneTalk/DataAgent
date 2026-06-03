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
                    "Get the schema information of the specified table, including column name, data"
                        + " type, whether it is primary key, whether it allows null, default value"
                        + " and column comments. This tool should be called to understand the table"
                        + " structure before generating SQL.")
    public ToolResult<TableSchemaSemanticPrompt> getTableSchema(
            @ToolParam(name = "table_name", description = "The table name to query schema for")
                    String tableName,
            @ToolParam(name = "column_page", description = "Optional column page number, default is 1") Integer columnPage,
            @ToolParam(name = "column_page_size", description = "Optional column page size, default is 20, maximum is 100")
                    Integer columnPageSize) {
        try {
            int resolvedPage = PageResponse.resolvePage(columnPage);
            int resolvedPageSize = PageResponse.resolvePageSize(columnPageSize);
            return ToolResult.success(
                    semanticMergeService.getTableSchema(tableName, resolvedPage, resolvedPageSize));
        } catch (IllegalStateException e) {
            return ToolResult.error("Data source parsing failed", e.getMessage());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid arguments", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Failed to get schema for table {}: {}", tableName, e.getMessage(), e);
            return ToolResult.error("Failed to retrieve schema", e.getMessage());
        }
    }
}

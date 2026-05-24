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
import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.service.ActiveDatasourceSupport;
import io.github.malonetalk.service.semantic.table.TableSemanticService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetTablesTool implements MarkAgentTool {

    private static final Logger logger = LoggerFactory.getLogger(GetTablesTool.class);

    private final ActiveDatasourceSupport activeDatasourceSupport;
    private final TableSemanticService tableSemanticService;

    public GetTablesTool(
            ActiveDatasourceSupport activeDatasourceSupport,
            TableSemanticService tableSemanticService) {
        this.activeDatasourceSupport = activeDatasourceSupport;
        this.tableSemanticService = tableSemanticService;
    }

    @Tool(
            name = "get_tables",
            description =
                    "Get visible tables as structured data. Returns a success/data/error wrapper,"
                            + " and data contains paged table items with name, domain, description,"
                            + " and the table's relations to other tables (relationType, source,"
                            + " sourceTableName, sourceColumnNames, targetTableName,"
                            + " targetColumnNames, description). Use this tool to discover both the"
                            + " available tables and how they join to each other.")
    public ToolResult<PageResponse<TablePromptResponse>> getTables(
            @ToolParam(name = "page", description = "Optional page number, defaults to 1")
                    Integer page,
            @ToolParam(
                            name = "page_size",
                            description = "Optional page size, defaults to 20 and max is 100")
                    Integer pageSize) {
        var datasourceResolution = activeDatasourceSupport.resolveActiveDatasource();
        if (!datasourceResolution.success()) {
            return ToolResult.error(
                    "ACTIVE_DATASOURCE_STATE_ERROR", datasourceResolution.message());
        }
        if (datasourceResolution.datasource() == null) {
            return ToolResult.error(
                    "NO_ACTIVE_DATASOURCE",
                    "No active datasource available, cannot query visible tables.");
        }
        try {
            return ToolResult.success(
                    tableSemanticService.getVisibleTablePromptPage(PageRequest.of(page, pageSize)));
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_PAGINATION_ARGUMENT", e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Failed to get visible tables: {}", e.getMessage(), e);
            return ToolResult.error("GET_TABLES_ERROR", e.getMessage());
        }
    }
}

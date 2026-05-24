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
import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.semantic.TableSchemaSemanticPrompt;
import io.github.malonetalk.exception.SemanticSchemaException;
import io.github.malonetalk.service.ActiveDatasourceSupport;
import io.github.malonetalk.service.semantic.table.TableSemanticService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetTableSchemaTool implements MarkAgentTool {

    private static final Logger logger = LoggerFactory.getLogger(GetTableSchemaTool.class);

    private final ActiveDatasourceSupport activeDatasourceSupport;
    private final TableSemanticService tableSemanticService;

    public GetTableSchemaTool(
            ActiveDatasourceSupport activeDatasourceSupport,
            TableSemanticService tableSemanticService) {
        this.activeDatasourceSupport = activeDatasourceSupport;
        this.tableSemanticService = tableSemanticService;
    }

    @Tool(
            name = "get_table_schema",
            description =
                    "Get the structured semantic schema information of the specified table."
                            + " Returns a success/data/error wrapper. Data includes table metadata"
                            + " and paged visible columns, with semantic descriptions taking"
                            + " precedence and physical metadata used as fallback. Table relations"
                            + " are returned together with the table list by get_tables, not by"
                            + " this tool. This tool should be called before generating SQL.")
    public ToolResult<TableSchemaSemanticPrompt> getTableSchema(
            @ToolParam(name = "table_name", description = "The table name to query schema for")
                    String tableName,
            @ToolParam(
                            name = "column_page",
                            description = "Optional column page number, defaults to 1")
                    Integer columnPage,
            @ToolParam(
                            name = "column_page_size",
                            description =
                                    "Optional column page size, defaults to 20 and max is 100")
                    Integer columnPageSize) {
        var datasourceResolution = activeDatasourceSupport.resolveActiveDatasource();
        if (!datasourceResolution.success()) {
            return ToolResult.error(
                    "ACTIVE_DATASOURCE_STATE_ERROR", datasourceResolution.message());
        }
        if (datasourceResolution.datasource() == null) {
            return ToolResult.error(
                    "NO_ACTIVE_DATASOURCE",
                    "No active datasource available, cannot query table schema.");
        }
        try {
            return ToolResult.success(
                    tableSemanticService.getTableSchema(
                            tableName, PageRequest.of(columnPage, columnPageSize)));
        } catch (IllegalArgumentException e) {
            return ToolResult.error("INVALID_PAGINATION_ARGUMENT", e.getMessage());
        } catch (SemanticSchemaException e) {
            return ToolResult.error("TABLE_SCHEMA_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Failed to get table schema for {}: {}", tableName, e.getMessage(), e);
            return ToolResult.error("TABLE_SCHEMA_INTERNAL_ERROR", e.getMessage());
        }
    }
}

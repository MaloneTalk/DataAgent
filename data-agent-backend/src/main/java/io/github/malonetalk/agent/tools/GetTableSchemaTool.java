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
import io.github.malonetalk.dto.semantic.TableSchemaSemanticPrompt;
import io.github.malonetalk.dto.tool.ToolResult;
import io.github.malonetalk.service.SemanticSchemaService;
import io.github.malonetalk.service.SemanticSchemaService.SemanticSchemaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetTableSchemaTool {

    private static final Logger logger = LoggerFactory.getLogger(GetTableSchemaTool.class);

    private final SemanticSchemaService semanticSchemaService;

    public GetTableSchemaTool(SemanticSchemaService semanticSchemaService) {
        this.semanticSchemaService = semanticSchemaService;
    }

    @Tool(
            name = "get_table_schema",
            description =
                    "Get the structured semantic schema information of the specified table."
                            + " Returns a success/data/error wrapper. Data includes table metadata"
                            + " and visible columns, with semantic descriptions taking precedence"
                            + " and physical metadata used as fallback. This tool should be called"
                            + " before generating SQL.")
    public ToolResult<TableSchemaSemanticPrompt> getTableSchema(
            @ToolParam(name = "table_name", description = "The table name to query schema for")
                    String tableName) {
        try {
            return ToolResult.success(semanticSchemaService.getTableSchema(tableName));
        } catch (SemanticSchemaException e) {
            return ToolResult.error("TABLE_SCHEMA_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Failed to get table schema for {}: {}", tableName, e.getMessage(), e);
            return ToolResult.error("TABLE_SCHEMA_INTERNAL_ERROR", e.getMessage());
        }
    }
}

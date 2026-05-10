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
import io.github.malonetalk.dto.tool.ToolResult;
import io.github.malonetalk.dto.toolresponse.TableSemanticPrompt;
import io.github.malonetalk.service.SemanticSchemaService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetTablesTool {

    private static final Logger logger = LoggerFactory.getLogger(GetTablesTool.class);

    private final SemanticSchemaService semanticSchemaService;

    public GetTablesTool(SemanticSchemaService semanticSchemaService) {
        this.semanticSchemaService = semanticSchemaService;
    }

    @Tool(
            name = "get_tables",
            description =
                    "Get visible tables as structured data. Returns a success/data/error wrapper,"
                            + " and data contains table items with name, domain, description.")
    public ToolResult<List<TableSemanticPrompt>> getTables() {
        try {
            return ToolResult.success(semanticSchemaService.getVisibleTablePrompts());
        } catch (RuntimeException e) {
            logger.error("Failed to get visible tables: {}", e.getMessage(), e);
            return ToolResult.error("GET_TABLES_ERROR", e.getMessage());
        }
    }
}

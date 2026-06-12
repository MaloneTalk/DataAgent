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
import io.github.malonetalk.agent.tools.response.ColumnPromptResponse;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.column.ColumnSemanticService;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class GetTableSchemaTool implements MarkAgentTool {

    private final DatasourceService dataSourceService;
    private final ColumnSemanticService columnSemanticService;

    @Tool(
            name = "get_table_schema",
            description =
                    "Get the schema information of the specified table, including column name, data"
                        + " type, whether it is primary key, whether it allows null, default value"
                        + " and column comments. Returns semantic-first merged column information"
                        + " (uses semantic layer if available, falls back to physical layer"
                        + " otherwise). This tool should be called to understand the table"
                        + " structure before generating SQL.")
    public String getTableSchema(
            @ToolParam(name = "table_name", description = "The table name to query schema for")
                    String tableName) {
        try {
            java.util.List<Datasource> activeDataSources =
                    dataSourceService.findByStatus(Status.ACTIVE.getCode());

            if (activeDataSources.isEmpty()) {
                return "No active datasource available, cannot get table schema.";
            }

            if (activeDataSources.size() > 1) {
                log.warn(
                        "Found {} active data sources, using the first one.",
                        activeDataSources.size());
            }

            Datasource datasource = activeDataSources.get(0);

            List<ColumnPromptResponse> columns =
                    columnSemanticService.getMergedTableSchema(datasource.getId(), tableName);
            return SemanticUtils.formatTableSchema(tableName, columns);
        } catch (Exception e) {
            log.error("Failed to get table schema: " + tableName, e);
            return "Failed to get table schema: " + e.getMessage();
        }
    }
}

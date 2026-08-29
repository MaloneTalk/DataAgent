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
import io.agentscope.core.tool.ToolParam;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.dto.TableExportResponse;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.ToolExceptionMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.TableExportService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ExportTableTool implements MarkAgentTool {

    private final DatasourceService datasourceService;
    private final TableExportService tableExportService;
    private final ToolExceptionMapper toolExceptionMapper;

    @Tool(
            name = "export_table",
            description =
                    """
                    Export a SELECT query result to a CSV spreadsheet file and return a download link.
                    Use this after large data queries or when the user asks to export data.
                    Exports are capped at 10000 rows; add filters or LIMIT for larger tables.
                    """)
    public ToolResultBlock exportTable(
            @ToolParam(name = "sql", description = "The SELECT SQL query statement to export")
                    String sql,
            @ToolParam(name = "title", description = "A short title for this export") String title,
            ToolCallContext ctx) {
        return toolExceptionMapper.run(
                () -> {
                    Datasource datasource =
                            datasourceService.getDatasourceForSession(ctx.sessionId());
                    TableExportResponse export =
                            tableExportService.create(ctx.sessionId(), datasource, title, sql);
                    return ToolResultBlock.text(formatResult(export));
                });
    }

    private String formatResult(TableExportResponse export) {
        return ToolCallConstants.SUCCESS_PREFIX
                + export.id()
                + "\nDownload URL: "
                + "/api/table-exports/"
                + export.id()
                + "/download"
                + "\nRows: "
                + export.rowCount();
    }
}

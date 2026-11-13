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
package io.github.malonetalk.agent.tools.impls;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.agent.tools.DataAgentTool;
import io.github.malonetalk.agent.tools.ToolCallConstants;
import io.github.malonetalk.agent.tools.ToolDatasourceResolver;
import io.github.malonetalk.agent.tools.ToolExceptionMapper;
import io.github.malonetalk.agent.tools.ToolResultBoConverter;
import io.github.malonetalk.dto.TableExportResponse;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.model.bo.SqlToolMetadataBo;
import io.github.malonetalk.model.bo.ToolResultBo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.PermissionService;
import io.github.malonetalk.service.TableExportService;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ExportTableTool implements DataAgentTool {

    private final PermissionService permissions;

    private final ToolDatasourceResolver datasourceResolver;
    private final TableExportService tableExportService;
    private final ToolExceptionMapper toolExceptionMapper;
    private final ObjectMapper objectMapper;

    @Tool(
            name = "export_table",
            converter = ToolResultBoConverter.class,
            description =
                    """
                    Export a SELECT query result to a CSV spreadsheet file and return a download link.
                    Use this after large data queries or when the user asks to export data.
                    Exports are capped at 10000 rows; add filters or LIMIT for larger tables.
                    """)
    public ToolResultBo exportTable(
            @ToolParam(
                            name = "datasource_id",
                            description =
                                    "Datasource id. Optional only when exactly one datasource is"
                                            + " available.",
                            required = false)
                    Integer datasourceId,
            @ToolParam(name = "sql", description = "The SELECT SQL query statement to export")
                    String sql,
            @ToolParam(name = "title", description = "A short title for this export") String title,
            ToolCallContext ctx) {
        return toolExceptionMapper.runResult(
                () -> {
                    permissions.require(ctx.userId(), PermissionEnum.ASSET_EXPORT_CREATE);
                    DatasourcePo datasource = datasourceResolver.resolve(ctx, datasourceId);
                    var result =
                            tableExportService.create(
                                    ctx.sessionId(), datasource, title, sql, ctx.userId());
                    var export = result.export();
                    var metadata =
                            SqlToolMetadataBo.builder()
                                    .datasourceId(datasource.getId())
                                    .datasourceName(datasource.getName())
                                    .databaseName(datasource.getDatabaseName())
                                    .executedSql(result.executedSql())
                                    .authorizedColumns(result.authorizedColumns())
                                    .returnedRows(export.rowCount())
                                    .truncated(false)
                                    .build();
                    Map<String, Object> resultMetadata =
                            objectMapper.convertValue(
                                    metadata, new TypeReference<Map<String, Object>>() {});
                    return new ToolResultBo(formatResult(export), resultMetadata);
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

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
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.table.TableSemanticService;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class GetTablesTool implements MarkAgentTool {

    private final DatasourceService dataSourceService;
    private final TableSemanticService tableSemanticService;

    @Tool(
            name = "get_tables",
            description =
                    "Get table information from the database, including table name, domain, description and relations. Returns semantic-first merged table information (uses semantic layer if available, falls back to physical layer otherwise).")
    public List<TablePromptResponse> getTables(
            @ToolParam(
                            name = "domains",
                            description =
                                    "Optional list of domain names. Only tables belonging to these domains will be returned. If not provided or empty, returns all tables.",
                            required = false)
                    List<String> domains) {
        List<Datasource> activeDataSources =
                dataSourceService.findByStatus(Status.ACTIVE.getCode());

        if (activeDataSources.isEmpty()) {
            return Collections.emptyList();
        }

        if (activeDataSources.size() > 1) {
            log.warn(
                    "Found {} active data sources, using the first one. This may cause data inconsistency.",
                    activeDataSources.size());
        }

        Datasource dataSource = activeDataSources.get(0);
        return tableSemanticService.listMergedTablesByDomains(dataSource.getId(), domains);
    }
}

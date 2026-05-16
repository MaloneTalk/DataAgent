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
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.TableInfoService;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetTablesTool {

    private static final Logger logger = LoggerFactory.getLogger(GetTablesTool.class);

    private final DatasourceService dataSourceService;
    private final TableInfoService tableInfoService;

    public GetTablesTool(DatasourceService dataSourceService, TableInfoService tableInfoService) {
        this.dataSourceService = dataSourceService;
        this.tableInfoService = tableInfoService;
    }

    @Tool(name = "get_tables", description = "获取数据库中的表信息，包括表名和表描述")
    public List<TableInfo> getTables() {
        List<Datasource> activeDataSources =
                dataSourceService.findByStatus(Status.ACTIVE.getCode());

        if (activeDataSources.isEmpty()) {
            return Collections.emptyList();
        }

        if (activeDataSources.size() > 1) {
            logger.warn(
                    "Found {} active data sources, using the first one. This may cause data"
                            + " inconsistency.",
                    activeDataSources.size());
        }

        Datasource dataSource = activeDataSources.get(0);
        return tableInfoService.findByDatasourceId(dataSource.getId());
    }
}

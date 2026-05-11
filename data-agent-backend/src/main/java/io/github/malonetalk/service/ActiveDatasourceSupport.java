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
package io.github.malonetalk.service;

import io.github.malonetalk.common.StatusConstants;
import io.github.malonetalk.entity.Datasource;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ActiveDatasourceSupport {

    private static final Logger logger = LoggerFactory.getLogger(ActiveDatasourceSupport.class);

    private final DatasourceService datasourceService;

    public ActiveDatasourceSupport(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    public Datasource getActiveDatasource() {
        List<Datasource> activeDataSources = datasourceService.findByStatus(StatusConstants.ACTIVE);
        if (activeDataSources.isEmpty()) {
            return null;
        }
        if (activeDataSources.size() > 1) {
            logger.warn(
                    "Found {} active data sources, using the first one with id={}.",
                    activeDataSources.size(),
                    activeDataSources.get(0).getId());
        }
        return activeDataSources.get(0);
    }
}

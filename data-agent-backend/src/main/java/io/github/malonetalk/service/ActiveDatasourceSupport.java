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

import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.enums.Status;
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
        ActiveDatasourceResolution resolution = resolveActiveDatasource();
        if (!resolution.success()) {
            throw new IllegalStateException(resolution.message());
        }
        return resolution.datasource();
    }

    public ActiveDatasourceResolution resolveActiveDatasource() {
        List<Datasource> activeDataSources =
                datasourceService.findByStatus(Status.ACTIVE.getCode());
        if (activeDataSources.isEmpty()) {
            return ActiveDatasourceResolution.success(null);
        }
        if (activeDataSources.size() > 1) {
            List<Integer> activeIds = activeDataSources.stream().map(Datasource::getId).toList();
            String message =
                    "Multiple active datasources found: "
                            + activeIds
                            + ". Please keep exactly one datasource active.";
            logger.error(
                    "Found {} active data sources, refusing to choose implicitly. activeIds={}",
                    activeDataSources.size(),
                    activeIds);
            return ActiveDatasourceResolution.error(message);
        }
        return ActiveDatasourceResolution.success(activeDataSources.get(0));
    }

    public record ActiveDatasourceResolution(
            boolean success, Datasource datasource, String message) {

        public static ActiveDatasourceResolution success(Datasource datasource) {
            return new ActiveDatasourceResolution(true, datasource, null);
        }

        public static ActiveDatasourceResolution error(String message) {
            return new ActiveDatasourceResolution(false, null, message);
        }
    }
}

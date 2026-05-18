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
package io.github.malonetalk.service.impl.semantic;

import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.SemanticSchemaException;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.SemanticDatasourceService;
import org.springframework.stereotype.Service;

@Service
public class SemanticDatasourceServiceImpl implements SemanticDatasourceService {

    private final DatasourceService datasourceService;

    public SemanticDatasourceServiceImpl(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    @Override
    public Datasource findDatasourceOrNull(Integer datasourceId) {
        return datasourceService.findById(datasourceId);
    }

    @Override
    public Datasource requireDatasource(Integer datasourceId) {
        Datasource datasource = findDatasourceOrNull(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
        return datasource;
    }

    @Override
    public Datasource requireSemanticDatasource(Integer datasourceId, String messagePrefix) {
        Datasource datasource = findDatasourceOrNull(datasourceId);
        if (datasource == null) {
            throw new SemanticSchemaException(messagePrefix + datasourceId);
        }
        return datasource;
    }

    @Override
    public void ensureWriteSuccess(boolean success, String message) {
        if (!success) {
            throw new SemanticSchemaException(message);
        }
    }
}

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
package io.github.malonetalk.convertor;

import io.github.malonetalk.dto.DatasourceRequest;
import io.github.malonetalk.dto.DatasourceResponse;
import io.github.malonetalk.entity.Datasource;
import org.springframework.stereotype.Component;

@Component
public class DatasourceConverter {

    public Datasource toEntity(DatasourceRequest request) {
        Datasource datasource = new Datasource();
        datasource.setName(request.name());
        datasource.setType(request.type());
        datasource.setHost(request.host());
        datasource.setPort(request.port());
        datasource.setDatabaseName(request.databaseName());
        datasource.setUsername(request.username());
        datasource.setPassword(request.password());
        datasource.setConnectionUrl(request.connectionUrl());
        datasource.setDescription(request.description());
        return datasource;
    }

    public DatasourceResponse toResponse(Datasource datasource) {
        return new DatasourceResponse(
                datasource.getId(),
                datasource.getName(),
                datasource.getType(),
                datasource.getHost(),
                datasource.getPort(),
                datasource.getDatabaseName(),
                datasource.getUsername(),
                datasource.getConnectionUrl(),
                datasource.getStatus(),
                datasource.getTestStatus(),
                datasource.getDescription(),
                datasource.getCreatorId(),
                datasource.getCreateTime(),
                datasource.getUpdateTime());
    }
}

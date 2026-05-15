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

import io.github.malonetalk.dto.TableInfoRequest;
import io.github.malonetalk.dto.TableInfoResponse;
import io.github.malonetalk.entity.TableInfo;
import org.springframework.stereotype.Component;

@Component
public class TableInfoConverter {

    public TableInfo toEntity(TableInfoRequest request) {
        TableInfo tableInfo = new TableInfo();
        tableInfo.setTableName(request.tableName());
        tableInfo.setTableDescription(request.tableDescription());
        tableInfo.setDomain(request.domain());
        tableInfo.setDatasourceId(request.datasourceId());
        tableInfo.setIsActive(request.isActive());
        return tableInfo;
    }

    public TableInfoResponse toResponse(TableInfo tableInfo) {
        return new TableInfoResponse(
                tableInfo.getId(),
                tableInfo.getTableName(),
                tableInfo.getTableDescription(),
                tableInfo.getDomain(),
                tableInfo.getDatasourceId(),
                tableInfo.getIsActive(),
                tableInfo.getCreateTime(),
                tableInfo.getUpdateTime());
    }
}

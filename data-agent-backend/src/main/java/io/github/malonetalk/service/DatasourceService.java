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

import io.github.malonetalk.enums.Status;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.dto.DatasourceDto;
import io.github.malonetalk.model.po.DatasourcePo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DatasourceService {

    List<DatasourcePo> findAll();

    Optional<DatasourcePo> findById(Integer id);

    default DatasourcePo requireById(Integer id) {
        return findById(id)
                .orElseThrow(
                        () ->
                                BusinessException.of(
                                        ErrorCode.RESOURCE_NOT_FOUND, "Datasource not found."));
    }

    void save(DatasourceDto request);

    void update(Integer id, DatasourceDto request);

    void deleteById(Integer id);

    List<DatasourcePo> findByStatus(String status);

    /** 按 ID 批量查询启用状态的数据源；空入参直接返回空列表。 */
    List<DatasourcePo> findActiveByIds(Collection<Integer> ids);

    List<DatasourcePo> findByType(String type);

    void updateStatus(Integer id, Status status);
}

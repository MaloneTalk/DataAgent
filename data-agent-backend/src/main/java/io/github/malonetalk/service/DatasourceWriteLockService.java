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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.DatasourceMapper;
import io.github.malonetalk.model.po.DatasourcePo;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DatasourceWriteLockService {
    private final DatasourceMapper datasources;

    // ponytail: serialize per datasource; use per-table coordination if contention warrants it.
    @Transactional(propagation = Propagation.MANDATORY)
    public DatasourcePo lock(Integer datasourceId) {
        return Optional.ofNullable(
                        datasources.selectOne(
                                Wrappers.<DatasourcePo>lambdaQuery()
                                        .eq(DatasourcePo::getId, datasourceId)
                                        .last("FOR UPDATE")))
                .orElseThrow(
                        () ->
                                BusinessException.of(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Datasource does not exist: " + datasourceId));
    }
}

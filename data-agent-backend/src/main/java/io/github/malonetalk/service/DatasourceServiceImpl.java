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
import io.github.malonetalk.agent.datasource.DataSourceType;
import io.github.malonetalk.agent.datasource.DynamicDataSourceManager;
import io.github.malonetalk.enums.DatasourceSourceType;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.DatasourceMapper;
import io.github.malonetalk.model.converter.DatasourceConverter;
import io.github.malonetalk.model.dto.DatasourceDto;
import io.github.malonetalk.model.po.DatasourcePo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceServiceImpl implements DatasourceService {

    private final DatasourceMapper dataSourceMapper;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final DatasourceConverter converter;

    @Override
    public List<DatasourcePo> findAll() {
        return dataSourceMapper.selectList(
                Wrappers.<DatasourcePo>lambdaQuery().orderByDesc(DatasourcePo::getCreateTime));
    }

    @Override
    public Optional<DatasourcePo> findById(Integer id) {
        return Optional.ofNullable(id).map(dataSourceMapper::selectById);
    }

    @Override
    public void save(DatasourceDto request) {
        DataSourceType type = requireDatasourceType(request.type());
        DatasourcePo datasource = converter.toPo(request);
        datasource.setType(type.getCode());
        datasource.setSourceType(DatasourceSourceType.DATABASE.name());
        datasource.setStatus(Status.INACTIVE.getCode());
        requireOperationSuccess(
                dataSourceMapper.insert(datasource), "Failed to save the datasource.");
    }

    @Override
    public void update(Integer id, DatasourceDto request) {
        DataSourceType type = requireDatasourceType(request.type());
        DatasourcePo datasource = requireById(id);
        requireEditable(datasource);
        converter.update(request, datasource);
        datasource.setType(type.getCode());
        // 与旧接口保持一致：null 不覆盖已有字段，空密码表示继续使用原密码。
        Optional.ofNullable(request.password())
                .filter(password -> !password.isEmpty())
                .ifPresent(datasource::setPassword);
        requireOperationSuccess(
                dataSourceMapper.updateById(datasource), "Failed to update the datasource.");
        dynamicDataSourceManager.removeDataSource(id);
    }

    @Override
    public void deleteById(Integer id) {
        requireEditable(requireById(id));
        requireOperationSuccess(
                dataSourceMapper.deleteById(id), "Failed to delete the datasource.");
        dynamicDataSourceManager.removeDataSource(id);
    }

    @Override
    public List<DatasourcePo> findByStatus(String status) {
        return dataSourceMapper.selectList(
                Wrappers.<DatasourcePo>lambdaQuery()
                        .eq(DatasourcePo::getStatus, status)
                        .orderByDesc(DatasourcePo::getCreateTime));
    }

    @Override
    public List<DatasourcePo> findActiveByIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return dataSourceMapper.selectList(
                Wrappers.<DatasourcePo>lambdaQuery()
                        .in(DatasourcePo::getId, ids)
                        .eq(DatasourcePo::getStatus, Status.ACTIVE.getCode()));
    }

    @Override
    public List<DatasourcePo> findByType(String type) {
        return dataSourceMapper.selectList(
                Wrappers.<DatasourcePo>lambdaQuery()
                        .eq(DatasourcePo::getType, type)
                        .orderByDesc(DatasourcePo::getCreateTime));
    }

    @Override
    public void updateStatus(Integer id, Status status) {
        requireById(id);
        DatasourcePo datasource = new DatasourcePo();
        datasource.setId(id);
        datasource.setStatus(status.getCode());
        requireOperationSuccess(
                dataSourceMapper.updateById(datasource), "Failed to update datasource status.");
    }

    private DataSourceType requireDatasourceType(String type) {
        return DataSourceType.fromCode(type)
                .orElseThrow(
                        () ->
                                BusinessException.of(
                                        ErrorCode.UNSUPPORTED_DATASOURCE_TYPE,
                                        "Unsupported datasource type: " + type));
    }

    private void requireEditable(DatasourcePo datasource) {
        if (DatasourceSourceType.CSV.name().equals(datasource.getSourceType())) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "CSV 托管数据源的连接由管理员配置，不能修改或删除。");
        }
    }

    private void requireOperationSuccess(int affectedRows, String message) {
        if (affectedRows == 0) {
            throw BusinessException.of(ErrorCode.OPERATION_FAILED, message);
        }
    }
}

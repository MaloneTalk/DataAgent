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

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.SessionDatasource;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.DatasourceMapper;
import io.github.malonetalk.mapper.SessionDatasourceMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class DatasourceServiceImpl implements DatasourceService {

    private final DatasourceMapper dataSourceMapper;
    private final SessionDatasourceMapper sessionDatasourceMapper;

    @Override
    public List<Datasource> findAll() {
        return dataSourceMapper.selectAll();
    }

    @Override
    public Datasource findById(Integer id) {
        return dataSourceMapper.selectById(id);
    }

    @Override
    public boolean save(Datasource dataSource) {
        dataSource.setCreateTime(LocalDateTime.now());
        dataSource.setUpdateTime(LocalDateTime.now());
        return dataSourceMapper.insert(dataSource) > 0;
    }

    @Override
    public boolean update(Datasource dataSource) {
        dataSource.setUpdateTime(LocalDateTime.now());
        return dataSourceMapper.update(dataSource) > 0;
    }

    @Override
    public boolean deleteById(Integer id) {
        return dataSourceMapper.deleteById(id) > 0;
    }

    @Override
    public List<Datasource> findByStatus(String status) {
        return dataSourceMapper.selectByStatus(status);
    }

    @Override
    public Optional<Datasource> getActiveDatasource() {
        List<Datasource> active = findByStatus(Status.ACTIVE.getCode());
        if (active.size() > 1) {
            log.warn("Found {} active data sources, using the first one.", active.size());
        }
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(0));
    }

    @Override
    public Datasource getDatasourceForSession(String sessionId) {
        SessionDatasource binding = sessionDatasourceMapper.selectBySessionId(sessionId);
        if (binding != null) {
            Datasource datasource = findExistingDatasource(binding.getDatasourceId());
            if (datasource == null) {
                throw BusinessException.of(ErrorCode.BOUND_DATASOURCE_UNAVAILABLE);
            }
            return datasource;
        }
        return getActiveDatasource()
                .orElseThrow(() -> BusinessException.of(ErrorCode.NO_ACTIVE_DATASOURCE));
    }

    @Override
    public void bindSessionDatasource(String sessionId, Integer datasourceId) {
        if (findExistingDatasource(datasourceId) == null) {
            throw BusinessException.of(
                    ErrorCode.BAD_REQUEST, "Datasource does not exist: " + datasourceId);
        }
        SessionDatasource binding = new SessionDatasource();
        binding.setSessionId(sessionId);
        binding.setDatasourceId(datasourceId);
        sessionDatasourceMapper.insertIfAbsent(binding);
    }

    private Datasource findExistingDatasource(Integer id) {
        return dataSourceMapper.selectById(id);
    }

    @Override
    public List<Datasource> findByType(String type) {
        return dataSourceMapper.selectByType(type);
    }

    @Override
    public boolean updateStatus(Integer id, String status) {
        return dataSourceMapper.updateStatus(id, status) > 0;
    }
}

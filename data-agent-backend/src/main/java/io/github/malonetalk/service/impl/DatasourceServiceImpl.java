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
package io.github.malonetalk.service.impl;

import io.github.malonetalk.agent.datasource.DynamicDataSourceManager;
import io.github.malonetalk.common.StatusConstants;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.mapper.DatasourceMapper;
import io.github.malonetalk.service.ActiveDatasourceLockManager;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.column.ColumnSemanticRepository;
import io.github.malonetalk.service.semantic.relation.RelationSemanticRepository;
import io.github.malonetalk.service.semantic.table.TableSemanticRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasourceServiceImpl implements DatasourceService {

    private final DatasourceMapper dataSourceMapper;
    private final TableSemanticRepository tableSemanticRepository;
    private final ColumnSemanticRepository columnSemanticRepository;
    private final RelationSemanticRepository relationSemanticRepository;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final ActiveDatasourceLockManager activeDatasourceLockManager;

    public DatasourceServiceImpl(
            DatasourceMapper dataSourceMapper,
            TableSemanticRepository tableSemanticRepository,
            ColumnSemanticRepository columnSemanticRepository,
            @Lazy RelationSemanticRepository relationSemanticRepository,
            DynamicDataSourceManager dynamicDataSourceManager,
            ActiveDatasourceLockManager activeDatasourceLockManager) {
        this.dataSourceMapper = dataSourceMapper;
        this.tableSemanticRepository = tableSemanticRepository;
        this.columnSemanticRepository = columnSemanticRepository;
        this.relationSemanticRepository = relationSemanticRepository;
        this.dynamicDataSourceManager = dynamicDataSourceManager;
        this.activeDatasourceLockManager = activeDatasourceLockManager;
    }

    @Override
    public List<Datasource> findAll() {
        return dataSourceMapper.selectAll();
    }

    @Override
    public Datasource findById(Integer id) {
        return dataSourceMapper.selectById(id);
    }

    @Override
    @Transactional
    public boolean save(Datasource dataSource) {
        guardActiveDatasourceTransition(null, dataSource.getStatus());
        dataSource.setCreateTime(LocalDateTime.now());
        dataSource.setUpdateTime(LocalDateTime.now());
        return dataSourceMapper.insert(dataSource) > 0;
    }

    @Override
    @Transactional
    public boolean update(Datasource dataSource) {
        if (dataSource.getId() == null) {
            return false;
        }
        Datasource existingDatasource = dataSourceMapper.selectById(dataSource.getId());
        if (existingDatasource == null) {
            return false;
        }
        String targetStatus =
                dataSource.getStatus() != null
                        ? dataSource.getStatus()
                        : existingDatasource.getStatus();
        guardActiveDatasourceTransition(existingDatasource.getId(), targetStatus);
        dataSource.setUpdateTime(LocalDateTime.now());
        boolean updated = dataSourceMapper.update(dataSource) > 0;
        if (updated) {
            dynamicDataSourceManager.removeDataSource(existingDatasource.getId());
        }
        return updated;
    }

    @Override
    @Transactional
    public boolean deleteById(Integer id) {
        Datasource existingDatasource = dataSourceMapper.selectById(id);
        if (existingDatasource == null) {
            return false;
        }
        relationSemanticRepository.deleteByDatasourceId(id);
        columnSemanticRepository.deleteByDatasourceId(id);
        tableSemanticRepository.deleteByDatasourceId(id);
        boolean deleted = dataSourceMapper.deleteById(id) > 0;
        if (deleted) {
            dynamicDataSourceManager.removeDataSource(id);
        }
        return deleted;
    }

    @Override
    public List<Datasource> findByStatus(String status) {
        return dataSourceMapper.selectByStatus(status);
    }

    @Override
    public List<Datasource> findByType(String type) {
        return dataSourceMapper.selectByType(type);
    }

    private void validateActiveDatasourceConstraint(Integer currentDatasourceId, String status) {
        if (!StatusConstants.ACTIVE.equalsIgnoreCase(status)) {
            return;
        }
        List<Integer> conflictingDatasourceIds =
                dataSourceMapper.selectByStatus(StatusConstants.ACTIVE).stream()
                        .map(Datasource::getId)
                        .filter(
                                id ->
                                        currentDatasourceId == null
                                                || !currentDatasourceId.equals(id))
                        .toList();
        if (!conflictingDatasourceIds.isEmpty()) {
            throw new IllegalStateException(
                    "Only one active datasource is allowed. Existing active datasource ids: "
                            + conflictingDatasourceIds);
        }
    }

    private void guardActiveDatasourceTransition(Integer currentDatasourceId, String targetStatus) {
        if (!StatusConstants.ACTIVE.equalsIgnoreCase(targetStatus)) {
            return;
        }
        activeDatasourceLockManager.acquireLock();
        validateActiveDatasourceConstraint(currentDatasourceId, targetStatus);
    }
}

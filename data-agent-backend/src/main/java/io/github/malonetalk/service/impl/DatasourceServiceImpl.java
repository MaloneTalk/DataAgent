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
import io.github.malonetalk.service.ColumnSemanticInfoService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.LogicalTableRelationService;
import io.github.malonetalk.service.TableInfoService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

@Service
public class DatasourceServiceImpl implements DatasourceService {

    private final DatasourceMapper dataSourceMapper;
    private final TableInfoService tableInfoService;
    private final ColumnSemanticInfoService columnSemanticInfoService;
    private final LogicalTableRelationService logicalTableRelationService;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final ActiveDatasourceLockManager activeDatasourceLockManager;

    public DatasourceServiceImpl(
            DatasourceMapper dataSourceMapper,
            TableInfoService tableInfoService,
            ColumnSemanticInfoService columnSemanticInfoService,
            @Lazy LogicalTableRelationService logicalTableRelationService,
            DynamicDataSourceManager dynamicDataSourceManager,
            ActiveDatasourceLockManager activeDatasourceLockManager) {
        this.dataSourceMapper = dataSourceMapper;
        this.tableInfoService = tableInfoService;
        this.columnSemanticInfoService = columnSemanticInfoService;
        this.logicalTableRelationService = logicalTableRelationService;
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
                dataSource.getStatus() != null ? dataSource.getStatus() : existingDatasource.getStatus();
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
        logicalTableRelationService.deleteByDatasourceId(id);
        columnSemanticInfoService.deleteByDatasourceId(id);
        tableInfoService.deleteByDatasourceId(id);
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
                        .filter(id -> currentDatasourceId == null || !currentDatasourceId.equals(id))
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

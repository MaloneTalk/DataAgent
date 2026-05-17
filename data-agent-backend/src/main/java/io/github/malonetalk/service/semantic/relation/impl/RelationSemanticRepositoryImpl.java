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
package io.github.malonetalk.service.semantic.relation.impl;

import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.service.semantic.relation.RelationSemanticRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RelationSemanticRepositoryImpl implements RelationSemanticRepository {

    private final LogicalTableRelationMapper logicalTableRelationMapper;

    public RelationSemanticRepositoryImpl(LogicalTableRelationMapper logicalTableRelationMapper) {
        this.logicalTableRelationMapper = logicalTableRelationMapper;
    }

    @Override
    public LogicalTableRelation findById(Integer id) {
        return logicalTableRelationMapper.selectById(id);
    }

    @Override
    public List<LogicalTableRelation> listByDatasourceId(Integer datasourceId) {
        return logicalTableRelationMapper.selectByDatasourceId(datasourceId);
    }

    @Override
    public List<LogicalTableRelation> listByDatasourceIdAndSourceTable(
            Integer datasourceId, String sourceTableName) {
        return logicalTableRelationMapper.selectByDatasourceIdAndSourceTable(
                datasourceId, sourceTableName);
    }

    @Override
    public long countByDatasourceIdAndSourceTable(
            Integer datasourceId, String sourceTableName, String keywordPrefix, Boolean enabled) {
        return logicalTableRelationMapper.countByDatasourceIdAndSourceTable(
                datasourceId, sourceTableName, keywordPrefix, enabled);
    }

    @Override
    public List<LogicalTableRelation> listPageByDatasourceIdAndSourceTable(
            Integer datasourceId,
            String sourceTableName,
            String keywordPrefix,
            Boolean enabled,
            boolean sortDescending,
            long offset,
            int limit) {
        return logicalTableRelationMapper.selectPageByDatasourceIdAndSourceTable(
                datasourceId,
                sourceTableName,
                keywordPrefix,
                enabled,
                sortDescending,
                offset,
                limit);
    }

    @Override
    public List<LogicalTableRelation> listEnabledByDatasourceIdAndSourceTable(
            Integer datasourceId, String sourceTableName) {
        return logicalTableRelationMapper.selectEnabledByDatasourceIdAndSourceTable(
                datasourceId, sourceTableName);
    }

    @Override
    public LogicalTableRelation findByUniqueSourceKey(
            Integer datasourceId, String sourceTableName, String sourceColumnSignature) {
        return logicalTableRelationMapper.selectByUniqueSourceKey(
                datasourceId, sourceTableName, sourceColumnSignature);
    }

    @Override
    public boolean save(LogicalTableRelation logicalTableRelation) {
        return logicalTableRelationMapper.insert(logicalTableRelation) > 0;
    }

    @Override
    public boolean update(LogicalTableRelation logicalTableRelation) {
        return logicalTableRelationMapper.update(logicalTableRelation) > 0;
    }

    @Override
    public boolean updateEnabled(
            Integer id,
            Integer datasourceId,
            String sourceTableName,
            Boolean enabled,
            LocalDateTime updateTime) {
        return logicalTableRelationMapper.updateEnabled(
                        id, datasourceId, sourceTableName, enabled, updateTime)
                > 0;
    }

    @Override
    public boolean deleteById(Integer id, Integer datasourceId, String sourceTableName) {
        return logicalTableRelationMapper.deleteById(id, datasourceId, sourceTableName) > 0;
    }

    @Override
    public int deleteByIdsAndSourceTable(
            Integer datasourceId, String sourceTableName, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return logicalTableRelationMapper.deleteByIdsAndSourceTable(
                datasourceId, sourceTableName, ids);
    }

    @Override
    public int deleteByIds(Integer datasourceId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return logicalTableRelationMapper.deleteByIds(datasourceId, ids);
    }

    @Override
    public int deleteByDatasourceId(Integer datasourceId) {
        return logicalTableRelationMapper.deleteByDatasourceId(datasourceId);
    }
}

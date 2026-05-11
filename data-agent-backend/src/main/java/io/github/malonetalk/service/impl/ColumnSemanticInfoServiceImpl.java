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

import io.github.malonetalk.entity.ColumnSemanticInfo;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.service.ColumnSemanticInfoService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ColumnSemanticInfoServiceImpl implements ColumnSemanticInfoService {

    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;

    public ColumnSemanticInfoServiceImpl(ColumnSemanticInfoMapper columnSemanticInfoMapper) {
        this.columnSemanticInfoMapper = columnSemanticInfoMapper;
    }

    @Override
    public List<ColumnSemanticInfo> findByDatasourceIdAndTableName(
            Integer datasourceId, String tableName) {
        return columnSemanticInfoMapper.selectByDatasourceIdAndTableName(datasourceId, tableName);
    }

    @Override
    public List<ColumnSemanticInfo> findByDatasourceId(Integer datasourceId) {
        return columnSemanticInfoMapper.selectByDatasourceId(datasourceId);
    }

    @Override
    public ColumnSemanticInfo findByDatasourceIdAndTableNameAndColumnName(
            Integer datasourceId, String tableName, String columnName) {
        return columnSemanticInfoMapper.selectByDatasourceIdAndTableNameAndColumnName(
                datasourceId, tableName, columnName);
    }

    @Override
    public boolean save(ColumnSemanticInfo columnSemanticInfo) {
        columnSemanticInfo.setCreateTime(LocalDateTime.now());
        columnSemanticInfo.setUpdateTime(LocalDateTime.now());
        return columnSemanticInfoMapper.insert(columnSemanticInfo) > 0;
    }

    @Override
    public boolean update(ColumnSemanticInfo columnSemanticInfo) {
        columnSemanticInfo.setUpdateTime(LocalDateTime.now());
        return columnSemanticInfoMapper.update(columnSemanticInfo) > 0;
    }

    @Override
    public int deleteByDatasourceId(Integer datasourceId) {
        return columnSemanticInfoMapper.deleteByDatasourceId(datasourceId);
    }

    @Override
    public boolean deleteByDatasourceIdAndTableNameAndColumnName(
            Integer datasourceId, String tableName, String columnName) {
        return columnSemanticInfoMapper.deleteByDatasourceIdAndTableNameAndColumnName(
                        datasourceId, tableName, columnName)
                > 0;
    }

    @Override
    public int deleteByDatasourceIdAndIds(Integer datasourceId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return columnSemanticInfoMapper.deleteByDatasourceIdAndIds(datasourceId, ids);
    }
}

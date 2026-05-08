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

import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.TableInfoMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TableInfoServiceImpl implements TableInfoService {

    private final TableInfoMapper tableInfoMapper;

    public TableInfoServiceImpl(TableInfoMapper tableInfoMapper) {
        this.tableInfoMapper = tableInfoMapper;
    }

    @Override
    public List<TableInfo> findAll() {
        return tableInfoMapper.selectAll();
    }

    @Override
    public TableInfo findById(Integer id) {
        return tableInfoMapper.selectById(id);
    }

    @Override
    public boolean save(TableInfo tableInfo) {
        tableInfo.setCreateTime(LocalDateTime.now());
        tableInfo.setUpdateTime(LocalDateTime.now());
        return tableInfoMapper.insert(tableInfo) > 0;
    }

    @Override
    public boolean update(TableInfo tableInfo) {
        tableInfo.setUpdateTime(LocalDateTime.now());
        return tableInfoMapper.update(tableInfo) > 0;
    }

    @Override
    public boolean deleteById(Integer id) {
        return tableInfoMapper.deleteById(id) > 0;
    }

    @Override
    public List<TableInfo> findByDatasourceId(Integer datasourceId) {
        return tableInfoMapper.selectByDatasourceId(datasourceId);
    }

    @Override
    public List<TableInfo> findByIsActive(Boolean isActive) {
        return tableInfoMapper.selectByIsActive(isActive);
    }

    @Override
    public List<TableInfo> findByDatasourceIdAndIsActive(Integer datasourceId, Boolean isActive) {
        return tableInfoMapper.selectByDatasourceIdAndIsActive(datasourceId, isActive);
    }
}

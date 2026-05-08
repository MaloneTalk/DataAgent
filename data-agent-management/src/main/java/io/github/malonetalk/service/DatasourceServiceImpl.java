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

import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.mapper.DatasourceMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DatasourceServiceImpl implements DatasourceService {

    private final DatasourceMapper dataSourceMapper;

    public DatasourceServiceImpl(DatasourceMapper dataSourceMapper) {
        this.dataSourceMapper = dataSourceMapper;
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
    public List<Datasource> findByType(String type) {
        return dataSourceMapper.selectByType(type);
    }
}

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
package io.github.malonetalk.service.impl.semantic.table;

import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.semantic.table.TableSemanticRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TableSemanticRepositoryImpl implements TableSemanticRepository {

    private final TableInfoMapper tableInfoMapper;

    public TableSemanticRepositoryImpl(TableInfoMapper tableInfoMapper) {
        this.tableInfoMapper = tableInfoMapper;
    }

    @Override
    public boolean save(TableInfo tableInfo) {
        if (tableInfo.getIsVisible() == null) {
            tableInfo.setIsVisible(true);
        }
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
    public int deleteByDatasourceId(Integer datasourceId) {
        return tableInfoMapper.deleteByDatasourceId(datasourceId);
    }

    @Override
    public int deleteByDatasourceIdAndIds(Integer datasourceId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return tableInfoMapper.deleteByDatasourceIdAndIds(datasourceId, ids);
    }

    @Override
    public List<TableInfo> listByDatasourceId(Integer datasourceId) {
        return tableInfoMapper.selectByDatasourceId(datasourceId);
    }

    @Override
    public TableInfo findByDatasourceIdAndTableName(Integer datasourceId, String tableName) {
        return tableInfoMapper.selectByDatasourceIdAndTableName(datasourceId, tableName);
    }
}

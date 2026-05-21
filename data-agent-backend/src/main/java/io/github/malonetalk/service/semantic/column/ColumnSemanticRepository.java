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
package io.github.malonetalk.service.semantic.column;

import io.github.malonetalk.entity.ColumnInfo;
import java.util.List;

/**
 * 列语义信息持久化接口
 */
public interface ColumnSemanticRepository {

    List<ColumnInfo> listByDatasourceId(Integer datasourceId);

    List<ColumnInfo> listByDatasourceIdAndTableName(Integer datasourceId, String tableName);

    List<ColumnInfo> listByDatasourceIdAndTableNameAndColumnNames(
            Integer datasourceId, String tableName, List<String> columnNames);

    ColumnInfo findByDatasourceIdAndTableNameAndColumnName(
            Integer datasourceId, String tableName, String columnName);

    boolean save(ColumnInfo columnInfo);

    boolean update(ColumnInfo columnInfo);

    int deleteByDatasourceId(Integer datasourceId);

    boolean deleteByDatasourceIdAndTableNameAndColumnName(
            Integer datasourceId, String tableName, String columnName);

    int deleteByDatasourceIdAndIds(Integer datasourceId, List<Integer> ids);
}

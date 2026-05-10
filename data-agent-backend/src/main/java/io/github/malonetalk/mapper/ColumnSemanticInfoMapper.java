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
package io.github.malonetalk.mapper;

import io.github.malonetalk.entity.ColumnSemanticInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ColumnSemanticInfoMapper {

    List<ColumnSemanticInfo> selectByDatasourceIdAndTableName(
            @Param("datasourceId") Integer datasourceId, @Param("tableName") String tableName);

    ColumnSemanticInfo selectByDatasourceIdAndTableNameAndColumnName(
            @Param("datasourceId") Integer datasourceId,
            @Param("tableName") String tableName,
            @Param("columnName") String columnName);

    int insert(ColumnSemanticInfo columnSemanticInfo);

    int update(ColumnSemanticInfo columnSemanticInfo);

    int deleteByDatasourceId(@Param("datasourceId") Integer datasourceId);

    int deleteByDatasourceIdAndTableNameAndColumnName(
            @Param("datasourceId") Integer datasourceId,
            @Param("tableName") String tableName,
            @Param("columnName") String columnName);
}

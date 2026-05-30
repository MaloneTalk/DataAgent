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

import io.github.malonetalk.entity.LogicalTableRelation;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LogicalTableRelationMapper {

    LogicalTableRelation selectById(@Param("id") Integer id);

    List<LogicalTableRelation> selectByDatasourceId(@Param("datasourceId") Integer datasourceId);

    List<LogicalTableRelation> selectByDatasourceIdAndSourceTable(
            @Param("datasourceId") Integer datasourceId,
            @Param("sourceTableName") String sourceTableName);

    long countByDatasourceIdAndSourceTable(
            @Param("datasourceId") Integer datasourceId,
            @Param("sourceTableName") String sourceTableName,
            @Param("keywordPrefix") String keywordPrefix,
            @Param("isEnabled") Boolean isEnabled);

    List<LogicalTableRelation> selectPageByDatasourceIdAndSourceTable(
            @Param("datasourceId") Integer datasourceId,
            @Param("sourceTableName") String sourceTableName,
            @Param("keywordPrefix") String keywordPrefix,
            @Param("isEnabled") Boolean isEnabled,
            @Param("sortDescending") boolean sortDescending,
            @Param("offset") long offset,
            @Param("limit") int limit);

    LogicalTableRelation selectByUniqueSourceKey(
            @Param("datasourceId") Integer datasourceId,
            @Param("sourceTableName") String sourceTableName,
            @Param("sourceColumnSignature") String sourceColumnSignature);

    int insert(LogicalTableRelation logicalTableRelation);

    int update(LogicalTableRelation logicalTableRelation);

    int updateEnabled(
            @Param("id") Integer id,
            @Param("datasourceId") Integer datasourceId,
            @Param("sourceTableName") String sourceTableName,
            @Param("isEnabled") Boolean isEnabled,
            @Param("updateTime") LocalDateTime updateTime);

    int deleteById(
            @Param("id") Integer id,
            @Param("datasourceId") Integer datasourceId,
            @Param("sourceTableName") String sourceTableName);

    int deleteByIdsAndSourceTable(
            @Param("datasourceId") Integer datasourceId,
            @Param("sourceTableName") String sourceTableName,
            @Param("ids") List<Integer> ids);

    int deleteByIds(@Param("datasourceId") Integer datasourceId, @Param("ids") List<Integer> ids);

    int deleteByDatasourceId(@Param("datasourceId") Integer datasourceId);
}

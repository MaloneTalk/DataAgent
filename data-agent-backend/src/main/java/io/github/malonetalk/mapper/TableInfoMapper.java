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

import io.github.malonetalk.dto.semantic.TableSemanticPageQuery;
import io.github.malonetalk.entity.TableInfo;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TableInfoMapper {

    int insert(TableInfo tableInfo);

    int updateSemanticFields(TableInfo tableInfo);

    int updatePhysicalCacheFields(TableInfo tableInfo);

    int deleteByDatasourceIdAndIds(
            @Param("datasourceId") Integer datasourceId, @Param("ids") List<Integer> ids);

    List<TableInfo> selectByDatasourceId(@Param("datasourceId") Integer datasourceId);

    List<TableInfo> selectPageByDatasourceId(
            @Param("query") TableSemanticPageQuery query,
            @Param("sortDescending") boolean sortDescending);

    TableInfo selectByDatasourceIdAndTableName(
            @Param("datasourceId") Integer datasourceId, @Param("tableName") String tableName);

    List<TableInfo> selectByDatasourceIdAndTableNames(
            @Param("datasourceId") Integer datasourceId,
            @Param("tableNames") List<String> tableNames);

    int batchUpsertPhysicalCache(@Param("tables") List<TableInfo> tables);

    int markPhysicalMissingByDatasourceIdAndTableNames(
            @Param("datasourceId") Integer datasourceId,
            @Param("tableNames") List<String> tableNames,
            @Param("now") LocalDateTime now);

    List<TableInfo> selectByDatasourceIdAndDomains(
            @Param("datasourceId") Integer datasourceId, @Param("domains") List<String> domains);

    int countByDomain(@Param("domain") String domain);
}

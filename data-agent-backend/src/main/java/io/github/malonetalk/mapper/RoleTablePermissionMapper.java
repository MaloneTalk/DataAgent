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

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.po.RoleHiddenColumn;
import io.github.malonetalk.model.po.RoleTablePermission;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.transaction.annotation.Transactional;

@Mapper
public interface RoleTablePermissionMapper {

    /** Serialize scope edits and publish table grants together with their hidden columns. */
    @Transactional
    default void replaceDataScope(
            Integer roleId,
            Integer datasourceId,
            List<String> tableNames,
            List<RoleHiddenColumn> hiddenColumns) {
        if (lockRole(roleId) == null) throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND);
        deleteByRoleAndDatasource(roleId, datasourceId);
        deleteHiddenByRoleAndDatasource(roleId, datasourceId);
        if (!tableNames.isEmpty()) insertTables(roleId, datasourceId, tableNames);
        if (!hiddenColumns.isEmpty()) insertHiddenColumns(hiddenColumns);
    }

    Integer lockRole(@Param("roleId") Integer roleId);

    int deleteHiddenByRoleAndDatasource(
            @Param("roleId") Integer roleId, @Param("datasourceId") Integer datasourceId);

    int insertTables(
            @Param("roleId") Integer roleId,
            @Param("datasourceId") Integer datasourceId,
            @Param("tableNames") List<String> tableNames);

    int insertHiddenColumns(@Param("columns") List<RoleHiddenColumn> columns);

    List<RoleTablePermission> selectByRoleId(@Param("roleId") Integer roleId);

    List<RoleTablePermission> selectByRoleAndDatasource(
            @Param("roleId") Integer roleId, @Param("datasourceId") Integer datasourceId);

    int deleteByRoleAndDatasource(
            @Param("roleId") Integer roleId, @Param("datasourceId") Integer datasourceId);

    int deleteByRoleId(@Param("roleId") Integer roleId);
}

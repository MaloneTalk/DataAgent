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

import static io.github.malonetalk.common.Constants.ADMIN_ROLE_ID;

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.convertor.RoleConverter;
import io.github.malonetalk.dto.ColumnPermissionResponse;
import io.github.malonetalk.dto.RoleRequest;
import io.github.malonetalk.dto.RoleResponse;
import io.github.malonetalk.dto.SaveColumnPermissionRequest;
import io.github.malonetalk.dto.SaveTablePermissionRequest;
import io.github.malonetalk.dto.TablePermissionResponse;
import io.github.malonetalk.entity.RoleHiddenColumn;
import io.github.malonetalk.entity.RoleTablePermission;
import io.github.malonetalk.entity.SysRole;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.mapper.SysRoleMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final RoleTablePermissionMapper roleTablePermissionMapper;
    private final RoleHiddenColumnMapper roleHiddenColumnMapper;
    private final RoleConverter roleConverter;

    @Override
    public List<RoleResponse> listAll() {
        return sysRoleMapper.selectAll().stream().map(roleConverter::toResponse).toList();
    }

    @Override
    public RoleResponse create(RoleRequest request) {
        checkNameConflict(request.name(), null);
        LocalDateTime now = LocalDateTime.now();
        SysRole role = new SysRole();
        role.setName(request.name());
        role.setDescription(request.description());
        role.setCreateTime(now);
        role.setUpdateTime(now);
        sysRoleMapper.insert(role);
        return roleConverter.toResponse(role);
    }

    @Override
    public RoleResponse update(Integer id, RoleRequest request) {
        SysRole role = requireRole(id);
        checkNameConflict(request.name(), id);
        role.setName(request.name());
        role.setDescription(request.description());
        sysRoleMapper.update(role);
        return roleConverter.toResponse(role);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        requireRole(id);
        if (id == ADMIN_ROLE_ID) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "不能删除管理员角色");
        }
        roleTablePermissionMapper.deleteByRoleId(id);
        roleHiddenColumnMapper.deleteByRoleId(id);
        sysRoleMapper.deleteById(id);
    }

    @Override
    public List<TablePermissionResponse> getPermissions(Integer roleId) {
        List<RoleTablePermission> perms = roleTablePermissionMapper.selectByRoleId(roleId);
        return perms.stream()
                .collect(
                        Collectors.groupingBy(
                                RoleTablePermission::getDatasourceId,
                                Collectors.mapping(
                                        RoleTablePermission::getTableName, Collectors.toList())))
                .entrySet()
                .stream()
                .map(e -> new TablePermissionResponse(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    @Transactional
    public void savePermissions(Integer roleId, SaveTablePermissionRequest request) {
        requireRole(roleId);
        roleTablePermissionMapper.deleteByRoleAndDatasource(roleId, request.datasourceId());
        if (request.tableNames() != null) {
            LocalDateTime now = LocalDateTime.now();
            for (String tableName : request.tableNames()) {
                RoleTablePermission perm = new RoleTablePermission();
                perm.setRoleId(roleId);
                perm.setDatasourceId(request.datasourceId());
                perm.setTableName(tableName);
                perm.setCreateTime(now);
                roleTablePermissionMapper.insert(perm);
            }
        }
    }

    @Override
    public List<ColumnPermissionResponse> getColumnPermissions(
            Integer roleId, Integer datasourceId) {
        List<RoleHiddenColumn> perms =
                roleHiddenColumnMapper.selectByRoleAndDatasource(roleId, datasourceId);
        return perms.stream()
                .collect(
                        Collectors.groupingBy(
                                RoleHiddenColumn::getTableName,
                                Collectors.mapping(
                                        RoleHiddenColumn::getColumnName, Collectors.toList())))
                .entrySet()
                .stream()
                .map(e -> new ColumnPermissionResponse(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    @Transactional
    public void saveColumnPermissions(Integer roleId, SaveColumnPermissionRequest request) {
        requireRole(roleId);
        roleHiddenColumnMapper.deleteByRoleDatasourceAndTable(
                roleId, request.datasourceId(), request.tableName());
        if (request.columnNames() != null && !request.columnNames().isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (String columnName : request.columnNames()) {
                RoleHiddenColumn perm = new RoleHiddenColumn();
                perm.setRoleId(roleId);
                perm.setDatasourceId(request.datasourceId());
                perm.setTableName(request.tableName());
                perm.setColumnName(columnName);
                perm.setCreateTime(now);
                roleHiddenColumnMapper.insert(perm);
            }
        }
    }

    private void checkNameConflict(String name, Integer excludeId) {
        List<SysRole> all = sysRoleMapper.selectAll();
        for (SysRole role : all) {
            if (role.getName().equals(name) && !role.getId().equals(excludeId)) {
                throw BusinessException.of(ErrorCode.DATA_CONFLICT, "角色名 '" + name + "' 已存在");
            }
        }
    }

    private SysRole requireRole(Integer id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return role;
    }
}

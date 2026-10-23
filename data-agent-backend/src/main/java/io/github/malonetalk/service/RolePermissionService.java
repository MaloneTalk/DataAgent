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

import io.github.malonetalk.dto.ColumnPermissionResponse;
import io.github.malonetalk.dto.SaveColumnPermissionRequest;
import io.github.malonetalk.dto.SaveTablePermissionRequest;
import io.github.malonetalk.dto.TablePermissionResponse;
import io.github.malonetalk.entity.RoleHiddenColumn;
import io.github.malonetalk.entity.RoleTablePermission;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.RoleCapabilityMapper;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.mapper.SysRoleMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.holder.UserContextHolder;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RolePermissionService {
    private final RoleCapabilityMapper mapper;
    private final RoleTablePermissionMapper roleTablePermissionMapper;
    private final RoleHiddenColumnMapper roleHiddenColumnMapper;
    private final TableInfoMapper tableMetadata;
    private final ColumnSemanticInfoMapper columnMetadata;
    private final SysRoleMapper roleMapper;
    private final PermissionService permissions;
    private final UserContextHolder holder;

    public Set<String> getCapabilities(Integer roleId) {
        requireRole(roleId);
        return Set.copyOf(mapper.selectCodes(roleId));
    }

    @Transactional
    public void saveCapabilities(Integer roleId, Set<String> codes) {
        var actor = requireGrantActor(roleId);
        codes.forEach(
                code -> {
                    var permission =
                            PermissionEnum.fromCode(code)
                                    .orElseThrow(
                                            () ->
                                                    BusinessException.of(
                                                            ErrorCode.BAD_REQUEST,
                                                            "Unknown permission: " + code));
                    if (permission.isSuperAdminOnly())
                        throw BusinessException.of(
                                ErrorCode.BAD_REQUEST, "Permission is super-admin only: " + code);
                });
        Set<String> previous = Set.copyOf(mapper.selectCodes(roleId));
        mapper.deleteByRoleId(roleId);
        codes.forEach(code -> mapper.insert(roleId, code));
        log.info(
                "Permission grant: actor={}, role={}, before={}, after={}",
                actor.userId(),
                roleId,
                previous,
                codes);
    }

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

    @Transactional
    public void savePermissions(Integer roleId, SaveTablePermissionRequest request) {
        var actor = requireGrantActor(roleId);
        log.info(
                "Data permission change: actor={}, role={}, request={}",
                actor.userId(),
                roleId,
                request);
        List<String> tableNames = Optional.ofNullable(request.tableNames()).orElseGet(List::of);
        if (!tableNames.isEmpty()) {
            var available =
                    tableMetadata.selectByDatasourceId(request.datasourceId()).stream()
                            .map(t -> t.getTableName())
                            .collect(Collectors.toSet());
            if (!available.containsAll(tableNames)
                    || new HashSet<>(tableNames).size() != tableNames.size()) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "Unknown or duplicate tables.");
            }
        }
        roleTablePermissionMapper.deleteByRoleAndDatasource(roleId, request.datasourceId());
        LocalDateTime now = LocalDateTime.now();
        for (String tableName : tableNames) {
            RoleTablePermission perm = new RoleTablePermission();
            perm.setRoleId(roleId);
            perm.setDatasourceId(request.datasourceId());
            perm.setTableName(tableName);
            perm.setCreateTime(now);
            roleTablePermissionMapper.insert(perm);
        }
    }

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

    @Transactional
    public void saveColumnPermissions(Integer roleId, SaveColumnPermissionRequest request) {
        var actor = requireGrantActor(roleId);
        log.info(
                "Data permission change: actor={}, role={}, request={}",
                actor.userId(),
                roleId,
                request);
        List<String> columnNames = Optional.ofNullable(request.columnNames()).orElseGet(List::of);
        if (!columnNames.isEmpty()) {
            var available =
                    columnMetadata
                            .selectByDatasourceIdAndTableName(
                                    request.datasourceId(), request.tableName())
                            .stream()
                            .map(c -> c.getColumnName())
                            .collect(Collectors.toSet());
            if (!available.containsAll(columnNames))
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "Unknown columns.");
        }
        roleHiddenColumnMapper.deleteByRoleDatasourceAndTable(
                roleId, request.datasourceId(), request.tableName());
        LocalDateTime now = LocalDateTime.now();
        for (String columnName : columnNames) {
            RoleHiddenColumn perm = new RoleHiddenColumn();
            perm.setRoleId(roleId);
            perm.setDatasourceId(request.datasourceId());
            perm.setTableName(request.tableName());
            perm.setColumnName(columnName);
            perm.setCreateTime(now);
            roleHiddenColumnMapper.insert(perm);
        }
    }

    public void deleteByRoleId(Integer roleId) {
        mapper.deleteByRoleId(roleId);
        roleTablePermissionMapper.deleteByRoleId(roleId);
        roleHiddenColumnMapper.deleteByRoleId(roleId);
    }

    private UserContextBo requireGrantActor(Integer roleId) {
        var actor = holder.checkAndGet();
        permissions.requireSuperAdmin(actor);
        requireRole(roleId);
        return actor;
    }

    private void requireRole(Integer roleId) {
        if (roleMapper.selectById(roleId) == null)
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND);
    }
}

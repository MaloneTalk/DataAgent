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

import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.RoleCapabilityMapper;
import io.github.malonetalk.mapper.SysRoleMapper;
import io.github.malonetalk.model.holder.UserContextHolder;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleCapabilityService {
    private final RoleCapabilityMapper mapper;
    private final SysRoleMapper roleMapper;
    private final PermissionService permissions;
    private final UserContextHolder holder;

    public Set<String> get(Integer roleId) {
        requireRole(roleId);
        return Set.copyOf(mapper.selectCodes(roleId));
    }

    @Transactional
    public void save(Integer roleId, Set<String> codes) {
        var actor = holder.checkAndGet();
        permissions.requireSuperAdmin(actor);
        requireRole(roleId);
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

    private void requireRole(Integer roleId) {
        if (roleMapper.selectById(roleId) == null)
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND);
    }
}

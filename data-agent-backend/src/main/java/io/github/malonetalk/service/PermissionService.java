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
import io.github.malonetalk.model.bo.UserContextBo;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final SysUserService sysUserService;
    private final RoleCapabilityMapper capabilityMapper;

    public UserContextBo user(Integer userId) {
        return Optional.ofNullable(userId)
                .map(sysUserService::selectAuthProjection)
                .orElseThrow(() -> BusinessException.of(ErrorCode.UNAUTHORIZED));
    }

    public Set<String> codes(UserContextBo user) {
        if (Boolean.TRUE.equals(user.superAdmin())) {
            return Arrays.stream(PermissionEnum.values())
                    .map(PermissionEnum::getCode)
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (user.roleId() == null || user.roleId() == 0) return Set.of();
        return capabilityMapper.selectCodes(user.roleId()).stream()
                .filter(
                        code ->
                                PermissionEnum.fromCode(code)
                                        .filter(p -> !p.isSuperAdminOnly())
                                        .isPresent())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean has(UserContextBo user, PermissionEnum permission) {
        return Boolean.TRUE.equals(user.superAdmin()) || codes(user).contains(permission.getCode());
    }

    public UserContextBo require(Integer userId, PermissionEnum... permissions) {
        UserContextBo user = user(userId);
        require(user, permissions);
        return user;
    }

    public void require(UserContextBo user, PermissionEnum... permissions) {
        Set<String> codes = codes(user);
        for (PermissionEnum permission : permissions) {
            if (!codes.contains(permission.getCode())) {
                throw BusinessException.of(
                        ErrorCode.FORBIDDEN, "Missing permission: " + permission.getCode());
            }
        }
    }

    public void requireSuperAdmin(UserContextBo user) {
        if (!Boolean.TRUE.equals(user.superAdmin()))
            throw BusinessException.of(ErrorCode.FORBIDDEN);
    }

    public void requireAny(UserContextBo user, PermissionEnum... permissions) {
        Set<String> codes = codes(user);
        if (Arrays.stream(permissions).map(PermissionEnum::getCode).noneMatch(codes::contains)) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "Missing required permission.");
        }
    }
}

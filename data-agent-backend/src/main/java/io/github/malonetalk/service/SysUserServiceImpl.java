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

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.dto.UserCreateRequest;
import io.github.malonetalk.dto.UserResponse;
import io.github.malonetalk.dto.UserUpdateRequest;
import io.github.malonetalk.entity.SysUser;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.SysRoleMapper;
import io.github.malonetalk.mapper.SysUserMapper;
import io.github.malonetalk.util.PasswordUtil;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    @Override
    public List<UserResponse> listAll() {
        return sysUserMapper.selectAll().stream().map(this::toResponse).toList();
    }

    @Override
    public UserResponse create(UserCreateRequest request) {
        SysUser existing = sysUserMapper.selectByUsername(request.username());
        if (existing != null) {
            throw BusinessException.of(
                    ErrorCode.DATA_CONFLICT, "用户名 '" + request.username() + "' 已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(PasswordUtil.hash(request.password()));
        user.setDisplayName(request.displayName());
        user.setRoleId(request.roleId());
        requireRoleIfAssigned(request.roleId());
        user.setIdpType("LOCAL");
        user.setStatus(1);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        sysUserMapper.insert(user);
        return toResponse(user);
    }

    @Override
    public UserResponse update(Integer id, UserUpdateRequest request) {
        SysUser user = requireUser(id);
        user.setDisplayName(request.displayName());
        if (request.roleId() != null) {
            requireRoleIfAssigned(request.roleId());
            user.setRoleId(request.roleId());
        }
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.update(user);
        return toResponse(user);
    }

    @Override
    public void resetPassword(Integer id, String newPassword) {
        SysUser user = requireUser(id);
        if (user.getPasswordHash() == null) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "外部身份源用户无法重置密码");
        }
        sysUserMapper.updatePassword(id, PasswordUtil.hash(newPassword), LocalDateTime.now());
    }

    @Override
    public void updateStatus(Integer id, Integer status) {
        requireUser(id);
        sysUserMapper.updateStatus(id, status);
    }

    private SysUser requireUser(Integer id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserResponse toResponse(SysUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoleId(),
                user.getStatus(),
                user.getCreateTime());
    }

    private void requireRoleIfAssigned(Integer roleId) {
        if (roleId == 0) return; // 0 = 未分配角色
        if (sysRoleMapper.selectById(roleId) == null) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "角色ID " + roleId + " 不存在");
        }
    }
}

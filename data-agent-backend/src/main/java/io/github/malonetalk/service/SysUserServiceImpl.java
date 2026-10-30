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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.SysRoleMapper;
import io.github.malonetalk.mapper.SysUserMapper;
import io.github.malonetalk.model.bo.SysUserBo;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.converter.UserConverter;
import io.github.malonetalk.model.dto.BaseBatchQueryDto;
import io.github.malonetalk.model.dto.UserCreateDto;
import io.github.malonetalk.model.dto.UserUpdateDto;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.po.SysUserPo;
import io.github.malonetalk.utils.PasswordUtil;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final UserContextHolder holder;
    private final SysRoleMapper sysRoleMapper;
    private final UserConverter userConverter;

    @Override
    public Optional<UserContextBo> selectAuthProjection(Integer userId) {
        return Optional.ofNullable(sysUserMapper.selectById(userId))
                .filter(user -> Integer.valueOf(1).equals(user.getStatus()))
                .map(userConverter::toContextBo);
    }

    @Override
    public SysUserBo bootstrapInitialAdmin(String initialPassword) {
        if (sysUserMapper.selectCount(null) > 0) {
            return null;
        }
        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException(
                    "No user exists and admin.init-password (env ADMIN_INIT_PASSWORD) is not set. "
                            + "Configure it before first startup to bootstrap the admin account.");
        }
        SysUserPo admin = new SysUserPo();
        admin.setUsername("admin");
        admin.setPasswordHash(PasswordUtil.hash(initialPassword));
        admin.setDisplayName("超级管理员");
        admin.setRoleId(0);
        admin.setSuperAdmin(true);
        admin.setIdpType("LOCAL");
        admin.setIdpUserId(null);
        admin.setStatus(1);
        sysUserMapper.insert(admin);
        return userConverter.toBo(admin);
    }

    @Override
    public SysUserBo findByUsername(String username) {
        return userConverter.toBo(selectLocalByUsername(username));
    }

    @Override
    public IPage<SysUserBo> page(BaseBatchQueryDto dto) {
        long current = dto.getPage() == null ? 1L : dto.getPage();
        long size = dto.getPageSize() == null ? 20L : dto.getPageSize();
        boolean asc = "asc".equalsIgnoreCase(dto.getSortOrder());
        Page<SysUserPo> page =
                sysUserMapper.selectPage(
                        new Page<>(current, size),
                        Wrappers.<SysUserPo>lambdaQuery().orderBy(true, asc, SysUserPo::getId));
        return page.convert(userConverter::toBo);
    }

    @Override
    @Transactional
    public SysUserBo create(UserCreateDto dto) {
        if (selectLocalByUsername(dto.username()) != null) {
            throw BusinessException.of(ErrorCode.DATA_CONFLICT, "用户名 '" + dto.username() + "' 已存在");
        }
        if (dto.roleId() != 0) requireAdminForRoleAssignment();
        lockRoleIfAssigned(dto.roleId());
        SysUserPo user = new SysUserPo();
        user.setUsername(dto.username());
        user.setPasswordHash(PasswordUtil.hash(dto.password()));
        user.setDisplayName(dto.displayName());
        user.setRoleId(dto.roleId());
        user.setIdpType("LOCAL");
        user.setStatus(1);
        sysUserMapper.insert(user);
        return userConverter.toBo(user);
    }

    @Override
    public SysUserBo update(Integer id, UserUpdateDto dto) {
        SysUserPo user = requireUser(id);
        protectAdmin(user);
        user.setDisplayName(dto.displayName());
        SysUserPo patch = new SysUserPo();
        patch.setId(id);
        patch.setDisplayName(dto.displayName());
        sysUserMapper.updateById(patch);
        return userConverter.toBo(user);
    }

    @Override
    @Transactional
    public void assignRole(Integer id, Integer roleId) {
        requireAdminForRoleAssignment();
        lockRoleIfAssigned(roleId);
        SysUserPo user = requireUser(id);
        Integer previous = user.getRoleId();
        SysUserPo patch = new SysUserPo();
        patch.setId(id);
        patch.setRoleId(roleId);
        sysUserMapper.updateById(patch);
        log.info(
                "Role assignment: actor={}, user={}, before={}, after={}",
                holder.checkAndGet().userId(),
                id,
                previous,
                roleId);
    }

    @Override
    public void changePassword(Integer userId, String oldPassword, String newPassword) {
        SysUserPo user = requireUser(userId);
        if (user.getPasswordHash() == null
                || !PasswordUtil.verify(oldPassword, user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "旧密码不正确");
        }
        updatePassword(userId, newPassword);
    }

    @Override
    public void resetPassword(Integer id, String newPassword) {
        SysUserPo user = requireUser(id);
        protectAdmin(user);
        if (user.getPasswordHash() == null) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "外部身份源用户无法重置密码");
        }
        updatePassword(id, newPassword);
    }

    @Override
    public void updateStatus(Integer id, Integer status) {
        protectAdmin(requireUser(id));
        SysUserPo patch = new SysUserPo();
        patch.setId(id);
        patch.setStatus(status);
        sysUserMapper.updateById(patch);
    }

    private void updatePassword(Integer id, String newPassword) {
        SysUserPo patch = new SysUserPo();
        patch.setId(id);
        patch.setPasswordHash(PasswordUtil.hash(newPassword));
        sysUserMapper.updateById(patch);
    }

    private SysUserPo selectLocalByUsername(String username) {
        return sysUserMapper.selectOne(
                Wrappers.<SysUserPo>lambdaQuery()
                        .eq(SysUserPo::getUsername, username)
                        .eq(SysUserPo::getIdpType, "LOCAL"));
    }

    private SysUserPo requireUser(Integer id) {
        SysUserPo user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void lockRoleIfAssigned(Integer roleId) {
        if (roleId == 0) return; // 0 = 未分配角色
        if (sysRoleMapper.lockById(roleId) == null) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "角色ID " + roleId + " 不存在");
        }
    }

    private void requireAdminForRoleAssignment() {
        if (!Boolean.TRUE.equals(holder.checkAndGet().superAdmin())) {
            throw BusinessException.of(
                    ErrorCode.FORBIDDEN, "Only super administrators can assign roles.");
        }
    }

    private void protectAdmin(SysUserPo target) {
        if (Boolean.TRUE.equals(target.getSuperAdmin())
                && !Boolean.TRUE.equals(holder.checkAndGet().superAdmin())) {
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
    }
}

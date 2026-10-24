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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.convertor.RoleConverter;
import io.github.malonetalk.dto.RoleRequest;
import io.github.malonetalk.dto.RoleResponse;
import io.github.malonetalk.entity.SysRole;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.SysRoleMapper;
import io.github.malonetalk.mapper.SysUserMapper;
import io.github.malonetalk.model.po.SysUserPo;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class SysRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper userMapper;
    private final RoleConverter roleConverter;
    private final RolePermissionService permissions;

    public List<RoleResponse> listAll() {
        return sysRoleMapper.selectAll().stream().map(roleConverter::toResponse).toList();
    }

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

    public RoleResponse update(Integer id, RoleRequest request) {
        SysRole role = requireRole(id);
        checkNameConflict(request.name(), id);
        role.setName(request.name());
        role.setDescription(request.description());
        sysRoleMapper.update(role);
        return roleConverter.toResponse(role);
    }

    @Transactional
    public void delete(Integer id) {
        requireRole(id);
        if (userMapper.selectCount(Wrappers.<SysUserPo>lambdaQuery().eq(SysUserPo::getRoleId, id))
                > 0) {
            throw BusinessException.of(ErrorCode.DATA_CONFLICT, "角色仍被用户使用");
        }
        permissions.deleteByRoleId(id);
        sysRoleMapper.deleteById(id);
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

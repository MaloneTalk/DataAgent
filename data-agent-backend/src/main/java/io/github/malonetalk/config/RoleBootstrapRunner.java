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
package io.github.malonetalk.config;

import io.github.malonetalk.entity.SysRole;
import io.github.malonetalk.mapper.SysRoleMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动引导：sys_role 为空时创建初始角色。
 *
 * <p>创建管理员（id=1）和普通用户（id=2）两个角色，
 * 与 {@code @AdminOnly} 注解配合实现管理员权限判定。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RoleBootstrapRunner implements CommandLineRunner {

    private final SysRoleMapper sysRoleMapper;

    @Override
    public void run(String... args) {
        if (sysRoleMapper.countAll() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        SysRole admin = new SysRole();
        admin.setId(1);
        admin.setName("管理员");
        admin.setDescription("系统管理员，拥有所有权限");
        admin.setCreateTime(now);
        admin.setUpdateTime(now);
        sysRoleMapper.insert(admin);

        SysRole user = new SysRole();
        user.setId(2);
        user.setName("普通用户");
        user.setDescription("普通用户，受限权限");
        user.setCreateTime(now);
        user.setUpdateTime(now);
        sysRoleMapper.insert(user);

        log.info("Bootstrapped initial roles: 管理员 (id=1), 普通用户 (id=2).");
    }
}

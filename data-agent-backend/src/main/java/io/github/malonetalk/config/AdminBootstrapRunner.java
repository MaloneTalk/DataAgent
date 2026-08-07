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

import io.github.malonetalk.entity.SysUser;
import io.github.malonetalk.mapper.SysUserMapper;
import io.github.malonetalk.util.PasswordUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动引导：sys_user 为空时创建初始 admin 账号。
 *
 * <p>初始密码取自环境变量（{@code ADMIN_INIT_PASSWORD}，经 {@code admin.init-password} 注入），
 * 不写死进代码/SQL，避免进 git 历史。登录后应立即用改密码接口换掉。
 *
 * <p>未配置初始密码时启动失败（fail-closed）——避免无密码 admin 账号被静默创建。
 * role_id 暂为 0：本轮无任何权限检查，admin 仅作为首个登录账号；
 * 「不受权限限制」语义随权限轮次 sys_role(id=1) + @AdminOnly 一起生效。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;

    @Value("${admin.init-password:}")
    private String adminInitPassword;

    @Override
    public void run(String... args) {
        if (sysUserMapper.countAll() > 0) {
            return;
        }
        if (adminInitPassword == null || adminInitPassword.isBlank()) {
            throw new IllegalStateException(
                    "No user exists and admin.init-password (env ADMIN_INIT_PASSWORD) is not set. "
                            + "Configure it before first startup to bootstrap the admin account.");
        }
        LocalDateTime now = LocalDateTime.now();
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPasswordHash(PasswordUtil.hash(adminInitPassword));
        admin.setDisplayName("管理员");
        admin.setRoleId(0);
        admin.setIdpType("LOCAL");
        admin.setIdpUserId(null);
        admin.setStatus(1);
        admin.setCreateTime(now);
        admin.setUpdateTime(now);
        sysUserMapper.insert(admin);
        log.info(
                "Bootstrapped initial admin account (id={}, username=admin). Change its password"
                        + " ASAP.",
                admin.getId());
    }
}

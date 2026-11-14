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

import io.github.malonetalk.model.bo.SysUserBo;
import io.github.malonetalk.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {

    private final SysUserService sysUserService;

    @Value("${admin.init-password:}")
    private String adminInitPassword;

    @Override
    public void run(String... args) {
        SysUserBo admin = sysUserService.bootstrapInitialAdmin(adminInitPassword);
        if (admin != null) {
            log.info(
                    "Bootstrapped initial super admin account (id={}, username=admin). Change its"
                            + " password ASAP.",
                    admin.getId());
        }
    }
}

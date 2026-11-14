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
package io.github.malonetalk;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.malonetalk.model.bo.SysUserBo;
import io.github.malonetalk.model.dto.BaseBatchQueryDto;
import io.github.malonetalk.service.SysUserService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 服务自检项：把仍是旧格式（非 BCrypt）的密码逐个提示重置。
 *
 * <p>会真实连接并修改数据库，且需终端交互，故默认跳过；仅在环境变量
 * {@code SELF_CHECK=true} 时运行（与其它自检项共用同一开关）。
 *
 * <pre>{@code SELF_CHECK=true mvn -f data-agent-backend/pom.xml clean test -Dtest=SysUserPasswordMigration -DforkCount=0}</pre>
 *
 * <p>IDEA 中运行请在 Run Configuration 里设置该环境变量，并在控制台输入新密码。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SELF_CHECK", matches = "true")
public class SysUserPasswordMigration {

    private static final Pattern BCRYPT =
            Pattern.compile("\\A\\$2[aby]?\\$\\d\\d\\$[./0-9A-Za-z]{53}");
    private static final int MAX_BYTES = 72;
    private static final int PAGE_SIZE = 200;

    @Autowired private SysUserService sysUserService;

    @Test
    void resetLegacyPasswords() throws IOException {
        List<SysUserBo> users = loadAllUsers();
        List<SysUserBo> legacyUsers =
                users.stream()
                        .filter(u -> u.getPasswordHash() != null)
                        .filter(u -> !isBcrypt(u.getPasswordHash()))
                        .toList();

        System.out.printf(
                "共 %d 个用户，其中 %d 个密码不是 BCrypt 格式，需要重置。%n", users.size(), legacyUsers.size());
        if (legacyUsers.isEmpty()) {
            System.out.println("无需迁移。");
            return;
        }

        BufferedReader reader =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        int reset = 0;
        for (SysUserBo user : legacyUsers) {
            printUser(user);
            Optional<String> newPassword = promptNewPassword(reader);
            if (newPassword.isEmpty()) {
                System.out.println("已跳过。");
                continue;
            }
            sysUserService.resetPassword(user.getId(), newPassword.get());
            reset++;
            System.out.printf("已重置用户 %s（id=%d）的密码。%n", user.getUsername(), user.getId());
        }
        System.out.printf("完成：共重置 %d / %d 个用户。%n", reset, legacyUsers.size());
    }

    private List<SysUserBo> loadAllUsers() {
        List<SysUserBo> all = new ArrayList<>();
        int current = 1;
        while (true) {
            BaseBatchQueryDto dto = new BaseBatchQueryDto();
            dto.setPage(current);
            dto.setPageSize(PAGE_SIZE);
            IPage<SysUserBo> page = sysUserService.page(dto);
            all.addAll(page.getRecords());
            if (current >= page.getPages()) {
                return all;
            }
            current++;
        }
    }

    private void printUser(SysUserBo user) {
        System.out.printf(
                "%n[id=%d] username=%s, displayName=%s, roleId=%s, idpType=%s, status=%s%n",
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoleId(),
                user.getIdpType(),
                user.getStatus());
    }

    private Optional<String> promptNewPassword(BufferedReader reader) throws IOException {
        while (true) {
            System.out.print("请输入新密码（直接回车跳过该用户）: ");
            System.out.flush();
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                return Optional.empty();
            }
            if (line.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES) {
                return Optional.of(line);
            }
            System.out.printf("密码不能超过 %d 字节，请重试。%n", MAX_BYTES);
        }
    }

    private static boolean isBcrypt(String hash) {
        return BCRYPT.matcher(hash).matches();
    }
}

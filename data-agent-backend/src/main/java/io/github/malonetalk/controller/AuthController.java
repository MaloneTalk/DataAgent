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
package io.github.malonetalk.controller;

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.Result;
import io.github.malonetalk.common.UserContext;
import io.github.malonetalk.dto.ChangePasswordRequest;
import io.github.malonetalk.dto.LoginRequest;
import io.github.malonetalk.dto.LoginResponse;
import io.github.malonetalk.dto.UserInfoResponse;
import io.github.malonetalk.entity.SysUser;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.SysUserMapper;
import io.github.malonetalk.util.JwtUtil;
import io.github.malonetalk.util.PasswordUtil;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录 / 当前用户 / 改密码。
 *
 * <p>登录失败统一返回 401 + "用户名或密码错误"，避免用户名枚举；账号禁用单独提示。
 */
@RestController
@Slf4j
@AllArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BAD_CREDENTIALS = "用户名或密码错误";

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        SysUser user = sysUserMapper.selectByUsername(request.username());
        // 用户不存在、外部身份源（password_hash 为空）、密码不匹配：统一文案，避免枚举用户名。
        if (user == null
                || user.getPasswordHash() == null
                || !PasswordUtil.verify(request.password(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "账号已禁用，请联系管理员");
        }
        String token = jwtUtil.generate(user.getId());
        UserInfoResponse info =
                new UserInfoResponse(user.getId(), user.getUsername(), user.getDisplayName());
        return Result.success(new LoginResponse(token, info));
    }

    @GetMapping("/me")
    public Result<UserInfoResponse> me() {
        UserContext context = UserContext.require();
        return Result.success(
                new UserInfoResponse(context.userId(), context.username(), context.displayName()));
    }

    @PostMapping("/change-password")
    public Result<Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Integer userId = UserContext.require().userId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null
                || user.getPasswordHash() == null
                || !PasswordUtil.verify(request.oldPassword(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "旧密码不正确");
        }
        sysUserMapper.updatePassword(
                userId, PasswordUtil.hash(request.newPassword()), LocalDateTime.now());
        return Result.success(true);
    }
}

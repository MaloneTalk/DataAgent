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

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.bo.SysUserBo;
import io.github.malonetalk.model.converter.UserConverter;
import io.github.malonetalk.model.dto.ChangePasswordDto;
import io.github.malonetalk.model.dto.LoginDto;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.vo.BooleanVo;
import io.github.malonetalk.model.vo.LoginVo;
import io.github.malonetalk.model.vo.UserInfoVo;
import io.github.malonetalk.service.SysUserService;
import io.github.malonetalk.utils.JwtUtil;
import io.github.malonetalk.utils.PasswordUtil;
import jakarta.validation.Valid;
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

    private final SysUserService sysUserService;
    private final JwtUtil jwtUtil;
    private final UserContextHolder userContextHolder;
    private final UserConverter userConverter;

    @PostMapping("/login")
    public LoginVo login(@Valid @RequestBody LoginDto dto) {
        SysUserBo user = sysUserService.findByUsername(dto.username());
        // 用户不存在、外部身份源（password_hash 为空）、密码不匹配：统一文案，避免枚举用户名。
        if (user == null
                || user.getPasswordHash() == null
                || !PasswordUtil.verify(dto.password(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "账号已禁用，请联系管理员");
        }
        String token = jwtUtil.generate(user.getId());
        return new LoginVo(token, userConverter.toInfoVo(user));
    }

    @GetMapping("/me")
    public UserInfoVo me() {
        return userConverter.toInfoVoFromContext(userContextHolder.checkAndGet());
    }

    @PostMapping("/change-password")
    public BooleanVo changePassword(@Valid @RequestBody ChangePasswordDto dto) {
        Integer userId = userContextHolder.checkAndGet().userId();
        sysUserService.changePassword(userId, dto.oldPassword(), dto.newPassword());
        return BooleanVo.TRUE;
    }
}

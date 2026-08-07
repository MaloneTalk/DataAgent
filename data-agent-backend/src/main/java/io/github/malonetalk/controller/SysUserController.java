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

import io.github.malonetalk.annotation.AdminOnly;
import io.github.malonetalk.common.Result;
import io.github.malonetalk.dto.ResetPasswordRequest;
import io.github.malonetalk.dto.UserCreateRequest;
import io.github.malonetalk.dto.UserResponse;
import io.github.malonetalk.dto.UserUpdateRequest;
import io.github.malonetalk.service.SysUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理 CRUD（权限轮次再加 @AdminOnly；本轮登录后即可用）。
 *
 * <p>username 唯一性由 Service 层保证；password 不允许通过 update 接口修改（需调用重置密码）。
 */
@AdminOnly
@RestController
@AllArgsConstructor
@RequestMapping("/api/sys/user")
@Validated
public class SysUserController {

    private final SysUserService sysUserService;

    @GetMapping
    public Result<List<UserResponse>> listAll() {
        return Result.success(sysUserService.listAll());
    }

    @PostMapping
    public Result<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.success(sysUserService.create(request));
    }

    @PutMapping("/{id}")
    public Result<UserResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UserUpdateRequest request) {
        return Result.success(sysUserService.update(id, request));
    }

    /** 管理员重置用户密码（不需旧密码）。 */
    @PutMapping("/{id}/password")
    public Result<Boolean> resetPassword(
            @PathVariable Integer id, @Valid @RequestBody ResetPasswordRequest request) {
        sysUserService.resetPassword(id, request.newPassword());
        return Result.success(true);
    }

    /** 启 / 停用户。 */
    @PutMapping("/{id}/status")
    public Result<Boolean> updateStatus(
            @PathVariable Integer id,
            @RequestParam
                    @jakarta.validation.constraints.Min(0)
                    @jakarta.validation.constraints.Max(1)
                    Integer status) {
        sysUserService.updateStatus(id, status);
        return Result.success(true);
    }
}

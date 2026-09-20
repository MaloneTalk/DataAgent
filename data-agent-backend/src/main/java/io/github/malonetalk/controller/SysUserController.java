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

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.malonetalk.annotation.RequirePermission;
import io.github.malonetalk.model.converter.BatchQueryConverter;
import io.github.malonetalk.model.converter.UserConverter;
import io.github.malonetalk.model.dto.BaseBatchQueryDto;
import io.github.malonetalk.model.dto.ResetPasswordDto;
import io.github.malonetalk.model.dto.UserCreateDto;
import io.github.malonetalk.model.dto.UserUpdateDto;
import io.github.malonetalk.model.vo.BatchQueryVo;
import io.github.malonetalk.model.vo.BooleanVo;
import io.github.malonetalk.model.vo.UserVo;
import io.github.malonetalk.service.SysUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequirePermission
@RestController
@AllArgsConstructor
@RequestMapping("/api/sys/user")
@Validated
public class SysUserController {

    private final SysUserService sysUserService;
    private final UserConverter userConverter;

    @GetMapping
    public BatchQueryVo<UserVo> list(@Valid BaseBatchQueryDto dto) {
        IPage<UserVo> page = sysUserService.page(dto).convert(userConverter::toVo);
        return BatchQueryConverter.toVo(page);
    }

    @PostMapping
    public UserVo create(@Valid @RequestBody UserCreateDto dto) {
        return userConverter.toVo(sysUserService.create(dto));
    }

    @PutMapping("/{id}")
    public UserVo update(@PathVariable Integer id, @Valid @RequestBody UserUpdateDto dto) {
        return userConverter.toVo(sysUserService.update(id, dto));
    }

    /** 管理员重置用户密码（不需旧密码）。 */
    @PutMapping("/{id}/password")
    public BooleanVo resetPassword(
            @PathVariable Integer id, @Valid @RequestBody ResetPasswordDto dto) {
        sysUserService.resetPassword(id, dto.newPassword());
        return BooleanVo.TRUE;
    }

    /** 启 / 停用户。 */
    @PutMapping("/{id}/status")
    public BooleanVo updateStatus(
            @PathVariable Integer id, @RequestParam @Min(0) @Max(1) Integer status) {
        sysUserService.updateStatus(id, status);
        return BooleanVo.TRUE;
    }
}

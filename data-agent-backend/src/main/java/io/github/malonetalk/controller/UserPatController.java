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

import io.github.malonetalk.annotation.RequirePermission;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.model.bo.UserPatCreateResultBo;
import io.github.malonetalk.model.converter.UserPatConverter;
import io.github.malonetalk.model.dto.UserPatCreateDto;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.vo.BooleanVo;
import io.github.malonetalk.model.vo.ListVo;
import io.github.malonetalk.model.vo.UserPatCreatedVo;
import io.github.malonetalk.model.vo.UserPatVo;
import io.github.malonetalk.service.UserPatService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/auth/pats")
public class UserPatController {

    private final UserPatService userPatService;
    private final UserPatConverter converter;
    private final UserContextHolder userContextHolder;

    @PostMapping
    @RequirePermission(PermissionEnum.ACCESS_PAT_MANAGE)
    public UserPatCreatedVo create(@Valid @RequestBody UserPatCreateDto request) {
        UserPatCreateResultBo result = userPatService.create(currentUserId(), request);
        return new UserPatCreatedVo(converter.toVo(result.pat()), result.token());
    }

    @GetMapping
    @RequirePermission(PermissionEnum.ACCESS_PAT_MANAGE)
    public ListVo<UserPatVo> list() {
        return new ListVo<>(
                userPatService.list(currentUserId()).stream().map(converter::toVo).toList());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionEnum.ACCESS_PAT_MANAGE)
    public BooleanVo revoke(@PathVariable Long id) {
        userPatService.revoke(currentUserId(), id);
        return BooleanVo.TRUE;
    }

    private Integer currentUserId() {
        return userContextHolder.checkAndGet().userId();
    }
}

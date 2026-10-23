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
import io.github.malonetalk.common.Result;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.model.dto.RoleCapabilitiesDto;
import io.github.malonetalk.model.vo.PermissionVo;
import io.github.malonetalk.service.RoleCapabilityService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sys")
public class PermissionController {
    private final RoleCapabilityService service;

    @RequirePermission(PermissionEnum.SYS_ROLE_READ)
    @GetMapping("/permissions")
    public Result<List<PermissionVo>> catalog() {
        return Result.success(
                Arrays.stream(PermissionEnum.values())
                        .map(
                                p ->
                                        new PermissionVo(
                                                p.getCode(),
                                                p.getName(),
                                                p.getModule(),
                                                p.getName(),
                                                p.isSuperAdminOnly()))
                        .toList());
    }

    @RequirePermission(PermissionEnum.SYS_ROLE_READ)
    @GetMapping("/role/{id}/capabilities")
    public Result<Set<String>> get(@PathVariable Integer id) {
        return Result.success(service.get(id));
    }

    @RequirePermission(PermissionEnum.SYS_ROLE_GRANT)
    @PutMapping("/role/{id}/capabilities")
    public Result<Void> save(
            @PathVariable Integer id, @Valid @RequestBody RoleCapabilitiesDto dto) {
        service.save(id, dto.codes());
        return Result.success();
    }
}

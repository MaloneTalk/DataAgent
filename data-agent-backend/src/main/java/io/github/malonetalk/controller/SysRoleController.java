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
import io.github.malonetalk.model.dto.RoleDataScopeDto;
import io.github.malonetalk.model.dto.RoleRequest;
import io.github.malonetalk.model.dto.SaveColumnPermissionRequest;
import io.github.malonetalk.model.dto.SaveTablePermissionRequest;
import io.github.malonetalk.model.vo.ColumnPermissionResponse;
import io.github.malonetalk.model.vo.RoleResponse;
import io.github.malonetalk.model.vo.TablePermissionResponse;
import io.github.malonetalk.service.RolePermissionService;
import io.github.malonetalk.service.SysRoleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/sys/role")
@Validated
public class SysRoleController {

    private final SysRoleService sysRoleService;
    private final RolePermissionService permissions;

    @GetMapping
    @RequirePermission(PermissionEnum.SYS_ROLE_READ)
    public Result<List<RoleResponse>> listAll() {
        return Result.success(sysRoleService.listAll());
    }

    @PostMapping
    @RequirePermission(PermissionEnum.SYS_ROLE_CREATE)
    public Result<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return Result.success(sysRoleService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionEnum.SYS_ROLE_UPDATE)
    public Result<RoleResponse> update(
            @PathVariable Integer id, @Valid @RequestBody RoleRequest request) {
        return Result.success(sysRoleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionEnum.SYS_ROLE_DELETE)
    public Result<Void> delete(@PathVariable Integer id) {
        sysRoleService.delete(id);
        return Result.success();
    }

    @GetMapping("/{roleId}/permissions")
    @RequirePermission(PermissionEnum.SYS_ROLE_READ)
    public Result<List<TablePermissionResponse>> getPermissions(@PathVariable Integer roleId) {
        return Result.success(permissions.getPermissions(roleId));
    }

    @PutMapping("/{roleId}/permissions")
    @RequirePermission(PermissionEnum.SYS_ROLE_GRANT)
    public Result<Void> savePermissions(
            @PathVariable Integer roleId, @Valid @RequestBody SaveTablePermissionRequest request) {
        permissions.savePermissions(roleId, request);
        return Result.success();
    }

    @PutMapping("/{roleId}/columns")
    @RequirePermission(PermissionEnum.SYS_ROLE_GRANT)
    public Result<Void> saveColumnPermissions(
            @PathVariable Integer roleId, @Valid @RequestBody SaveColumnPermissionRequest request) {
        permissions.saveColumnPermissions(roleId, request);
        return Result.success();
    }

    @PutMapping("/{roleId}/data-scope")
    @RequirePermission(PermissionEnum.SYS_ROLE_GRANT)
    public Result<Void> saveDataScope(
            @PathVariable Integer roleId, @Valid @RequestBody RoleDataScopeDto request) {
        permissions.saveDataScope(roleId, request);
        return Result.success();
    }

    @GetMapping("/{roleId}/columns")
    @RequirePermission(PermissionEnum.SYS_ROLE_READ)
    public Result<List<ColumnPermissionResponse>> getColumnPermissions(
            @PathVariable Integer roleId, @RequestParam Integer datasourceId) {
        return Result.success(permissions.getColumnPermissions(roleId, datasourceId));
    }

    @RequirePermission(PermissionEnum.SYS_ROLE_READ)
    @GetMapping("/{id}/capabilities")
    public Result<Set<String>> getCapabilities(@PathVariable Integer id) {
        return Result.success(permissions.getCapabilities(id));
    }

    @RequirePermission(PermissionEnum.SYS_ROLE_GRANT)
    @PutMapping("/{id}/capabilities")
    public Result<Void> saveCapabilities(
            @PathVariable Integer id, @Valid @RequestBody RoleCapabilitiesDto dto) {
        permissions.saveCapabilities(id, dto.codes());
        return Result.success();
    }
}

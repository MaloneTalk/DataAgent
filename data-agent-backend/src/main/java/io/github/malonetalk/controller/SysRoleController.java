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
import io.github.malonetalk.dto.ColumnPermissionResponse;
import io.github.malonetalk.dto.RoleRequest;
import io.github.malonetalk.dto.RoleResponse;
import io.github.malonetalk.dto.SaveColumnPermissionRequest;
import io.github.malonetalk.dto.SaveTablePermissionRequest;
import io.github.malonetalk.dto.TablePermissionResponse;
import io.github.malonetalk.service.SysRoleService;
import jakarta.validation.Valid;
import java.util.List;
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

@AdminOnly
@RestController
@AllArgsConstructor
@RequestMapping("/api/sys/role")
@Validated
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping
    public Result<List<RoleResponse>> listAll() {
        return Result.success(sysRoleService.listAll());
    }

    @PostMapping
    public Result<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return Result.success(sysRoleService.create(request));
    }

    @PutMapping("/{id}")
    public Result<RoleResponse> update(
            @PathVariable Integer id, @Valid @RequestBody RoleRequest request) {
        return Result.success(sysRoleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        sysRoleService.delete(id);
        return Result.success();
    }

    @GetMapping("/{roleId}/permissions")
    public Result<List<TablePermissionResponse>> getPermissions(@PathVariable Integer roleId) {
        return Result.success(sysRoleService.getPermissions(roleId));
    }

    @PutMapping("/{roleId}/permissions")
    public Result<Void> savePermissions(
            @PathVariable Integer roleId, @Valid @RequestBody SaveTablePermissionRequest request) {
        sysRoleService.savePermissions(roleId, request);
        return Result.success();
    }

    @GetMapping("/{roleId}/columns")
    public Result<List<ColumnPermissionResponse>> getColumnPermissions(
            @PathVariable Integer roleId, @RequestParam Integer datasourceId) {
        return Result.success(sysRoleService.getColumnPermissions(roleId, datasourceId));
    }

    @PutMapping("/{roleId}/columns")
    public Result<Void> saveColumnPermissions(
            @PathVariable Integer roleId, @Valid @RequestBody SaveColumnPermissionRequest request) {
        sysRoleService.saveColumnPermissions(roleId, request);
        return Result.success();
    }
}

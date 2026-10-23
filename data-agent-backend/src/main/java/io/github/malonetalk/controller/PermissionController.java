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
import io.github.malonetalk.model.vo.PermissionVo;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sys")
public class PermissionController {
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
}

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
package io.github.malonetalk.service;

import io.github.malonetalk.dto.ColumnPermissionResponse;
import io.github.malonetalk.dto.RoleRequest;
import io.github.malonetalk.dto.RoleResponse;
import io.github.malonetalk.dto.SaveColumnPermissionRequest;
import io.github.malonetalk.dto.SaveTablePermissionRequest;
import io.github.malonetalk.dto.TablePermissionResponse;
import java.util.List;

public interface SysRoleService {

    List<RoleResponse> listAll();

    RoleResponse create(RoleRequest request);

    RoleResponse update(Integer id, RoleRequest request);

    void delete(Integer id);

    List<TablePermissionResponse> getPermissions(Integer roleId);

    void savePermissions(Integer roleId, SaveTablePermissionRequest request);

    List<ColumnPermissionResponse> getColumnPermissions(Integer roleId, Integer datasourceId);

    void saveColumnPermissions(Integer roleId, SaveColumnPermissionRequest request);
}

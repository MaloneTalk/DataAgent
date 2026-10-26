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

import io.github.malonetalk.model.dto.RoleDataScopeDto;
import io.github.malonetalk.model.dto.SaveColumnPermissionRequest;
import io.github.malonetalk.model.dto.SaveTablePermissionRequest;
import io.github.malonetalk.model.vo.ColumnPermissionResponse;
import io.github.malonetalk.model.vo.TablePermissionResponse;
import java.util.List;
import java.util.Set;

public interface RolePermissionService {
    Set<String> getCapabilities(Integer roleId);

    void saveCapabilities(Integer roleId, Set<String> codes);

    List<TablePermissionResponse> getPermissions(Integer roleId);

    void saveDataScope(Integer roleId, RoleDataScopeDto request);

    void savePermissions(Integer roleId, SaveTablePermissionRequest request);

    void saveColumnPermissions(Integer roleId, SaveColumnPermissionRequest request);

    List<ColumnPermissionResponse> getColumnPermissions(Integer roleId, Integer datasourceId);

    void deleteByRoleId(Integer roleId);
}

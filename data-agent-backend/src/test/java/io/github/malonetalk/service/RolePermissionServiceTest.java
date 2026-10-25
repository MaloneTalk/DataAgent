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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.malonetalk.convertor.RoleConverter;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.RoleCapabilityMapper;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.mapper.SysRoleMapper;
import io.github.malonetalk.mapper.SysUserMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.dto.RoleDataScopeDto;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.po.SysRole;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RolePermissionServiceTest {
    private final RoleCapabilityMapper capabilities = mock(RoleCapabilityMapper.class);
    private final RoleTablePermissionMapper tables = mock(RoleTablePermissionMapper.class);
    private final RoleHiddenColumnMapper columns = mock(RoleHiddenColumnMapper.class);
    private final TableInfoMapper tableMetadata = mock(TableInfoMapper.class);
    private final ColumnSemanticInfoMapper columnMetadata = mock(ColumnSemanticInfoMapper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final PermissionService permissions =
            new PermissionService(mock(SysUserService.class), capabilities);
    private final UserContextHolder holder = new UserContextHolder();
    private final RolePermissionService service =
            new RolePermissionService(
                    capabilities,
                    tables,
                    columns,
                    tableMetadata,
                    columnMetadata,
                    roles,
                    permissions,
                    holder);

    @Test
    void everyGrantTypeUsesTheSameSuperAdminGuard() {
        holder.set(UserContextBo.builder().userId(7).superAdmin(false).build());
        assertThrows(BusinessException.class, () -> service.saveCapabilities(2, Set.of()));
        assertThrows(
                BusinessException.class,
                () -> service.saveDataScope(2, new RoleDataScopeDto(1, List.of(), Map.of())));
        verifyNoInteractions(capabilities, tables, columns, roles);
    }

    @Test
    void emptyScopeStillRevokesGrants() {
        holder.set(UserContextBo.builder().userId(1).superAdmin(true).build());
        when(roles.selectById(2)).thenReturn(new SysRole());
        service.saveCapabilities(2, Set.of());
        service.saveDataScope(2, new RoleDataScopeDto(1, List.of(), Map.of()));
        verify(capabilities).deleteByRoleId(2);
        verify(tables).replaceDataScope(2, 1, List.of(), List.of());
        verifyNoInteractions(columns);
    }

    @Test
    void invalidCapabilitiesAreRejectedBeforeRemovingPreviousGrants() {
        holder.set(UserContextBo.builder().userId(1).superAdmin(true).build());
        when(roles.selectById(2)).thenReturn(new SysRole());
        assertThrows(BusinessException.class, () -> service.saveCapabilities(2, Set.of("unknown")));
        assertThrows(
                BusinessException.class,
                () -> service.saveCapabilities(2, Set.of("sys:role:grant")));
        verify(capabilities, never()).deleteByRoleId(2);
    }

    @Test
    void roleDeletionCleansEveryGrantTypeBeforeDeletingTheRole() {
        when(roles.selectById(2)).thenReturn(new SysRole());
        SysUserMapper users = mock(SysUserMapper.class);
        when(users.selectCount(any())).thenReturn(0L);
        var roleService = new SysRoleService(roles, users, mock(RoleConverter.class), service);
        roleService.delete(2);
        var order = inOrder(capabilities, tables, columns, roles);
        order.verify(capabilities).deleteByRoleId(2);
        order.verify(tables).deleteByRoleId(2);
        order.verify(columns).deleteByRoleId(2);
        order.verify(roles).deleteById(2);
    }
}

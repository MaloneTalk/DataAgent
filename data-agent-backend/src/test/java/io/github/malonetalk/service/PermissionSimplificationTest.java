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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.RoleHiddenColumn;
import io.github.malonetalk.entity.RoleTablePermission;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.RoleCapabilityMapper;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionSimplificationTest {
    private final SysUserService users = mock(SysUserService.class);
    private final RoleCapabilityMapper capabilities = mock(RoleCapabilityMapper.class);
    private final RoleTablePermissionMapper tables = mock(RoleTablePermissionMapper.class);
    private final RoleHiddenColumnMapper columns = mock(RoleHiddenColumnMapper.class);
    private final ColumnSemanticInfoMapper metadata = mock(ColumnSemanticInfoMapper.class);
    private final PermissionService permissions = new PermissionService(users, capabilities);
    private final DataPermissionService data = new DataPermissionService(tables, columns);
    private final UserContextBo user =
            UserContextBo.builder().userId(7).roleId(2).superAdmin(false).build();

    @Test
    void sharedSqlGateLoadsIdentityOnceAndStillRejectsRevocationAndHiddenColumns() {
        when(users.selectAuthProjection(7)).thenReturn(user);
        when(capabilities.selectCodes(2)).thenReturn(List.of("analysis:sql:execute"));
        RoleTablePermission table = new RoleTablePermission();
        table.setTableName("orders");
        when(tables.selectByRoleAndDatasource(2, 1)).thenReturn(List.of(table));
        RoleHiddenColumn hidden = new RoleHiddenColumn();
        hidden.setTableName("orders");
        hidden.setColumnName("phone");
        when(columns.selectByRoleAndDatasource(2, 1)).thenReturn(List.of(hidden));
        ColumnInfo id = new ColumnInfo();
        id.setColumnName("id");
        when(metadata.selectByDatasourceIdAndTableName(1, "orders")).thenReturn(List.of(id));
        Datasource datasource = new Datasource();
        datasource.setId(1);
        SqlPermissionService sql = new SqlPermissionService(permissions, data, metadata);

        assertTrue(
                sql.authorize(7, datasource, "SELECT * FROM orders")
                        .contains("SELECT `id` FROM `orders`"));
        verify(users).selectAuthProjection(7);
        assertThrows(
                BusinessException.class,
                () -> sql.authorize(7, datasource, "SELECT phone FROM orders"));
        when(capabilities.selectCodes(2)).thenReturn(List.of());
        assertThrows(
                BusinessException.class,
                () -> sql.authorize(7, datasource, "SELECT * FROM orders"));
        when(users.selectAuthProjection(7)).thenReturn(null);
        assertThrows(
                BusinessException.class,
                () -> sql.authorize(7, datasource, "SELECT * FROM orders"));
    }

    @Test
    void allRequiredPermissionsAndReservedPermissionsStillApply() {
        when(users.selectAuthProjection(7)).thenReturn(user);
        when(capabilities.selectCodes(2))
                .thenReturn(List.of("analysis:chat:use", "sys:role:grant", "unknown"));
        assertSame(user, permissions.require(7, PermissionEnum.ANALYSIS_CHAT_USE));
        assertThrows(
                BusinessException.class,
                () ->
                        permissions.require(
                                7,
                                PermissionEnum.ANALYSIS_CHAT_USE,
                                PermissionEnum.ANALYSIS_SQL_EXECUTE));
        assertThrows(
                BusinessException.class,
                () -> permissions.require(7, PermissionEnum.SYS_ROLE_GRANT));
        assertEquals(1, permissions.codes(user).size());
        var admin = UserContextBo.builder().userId(1).superAdmin(true).build();
        permissions.require(admin, PermissionEnum.SYS_ROLE_GRANT);
    }

    @Test
    void hiddenColumnListIsLoadedOnceForAllReferencedColumns() {
        RoleHiddenColumn hidden = new RoleHiddenColumn();
        hidden.setTableName("orders");
        hidden.setColumnName("phone");
        when(columns.selectByRoleAndDatasource(2, 1)).thenReturn(List.of(hidden));
        assertTrue(data.hasHiddenColumns(user, 1, "orders", List.of("id", "amount", "`PHONE`")));
        verify(columns, times(1)).selectByRoleAndDatasource(2, 1);
    }
}

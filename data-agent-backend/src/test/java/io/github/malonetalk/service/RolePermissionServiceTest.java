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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.malonetalk.convertor.RoleConverter;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
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
import io.github.malonetalk.model.dto.SaveColumnPermissionRequest;
import io.github.malonetalk.model.dto.SaveTablePermissionRequest;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.po.RoleHiddenColumn;
import io.github.malonetalk.model.po.RoleTablePermission;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        assertThrows(
                BusinessException.class,
                () -> service.savePermissions(2, new SaveTablePermissionRequest(1, List.of())));
        assertThrows(
                BusinessException.class,
                () ->
                        service.saveColumnPermissions(
                                2, new SaveColumnPermissionRequest(1, "orders", List.of())));
        verifyNoInteractions(capabilities, tables, columns, roles);
    }

    @Test
    void emptyScopeStillRevokesGrants() {
        holder.set(UserContextBo.builder().userId(1).superAdmin(true).build());
        when(roles.lockById(2)).thenReturn(2);
        service.saveCapabilities(2, Set.of());
        service.saveDataScope(2, new RoleDataScopeDto(1, List.of(), Map.of()));
        verify(capabilities).deleteByRoleId(2);
        verify(tables).deleteByRoleAndDatasource(2, 1);
        verify(columns).deleteByRoleAndDatasource(2, 1);
        verify(tables, never()).insertTables(any(), any(), any());
        verify(columns, never()).insertColumns(any());
    }

    @Test
    void invalidCapabilitiesAreRejectedBeforeRemovingPreviousGrants() {
        holder.set(UserContextBo.builder().userId(1).superAdmin(true).build());
        when(roles.lockById(2)).thenReturn(2);
        assertThrows(BusinessException.class, () -> service.saveCapabilities(2, Set.of("unknown")));
        assertThrows(
                BusinessException.class,
                () -> service.saveCapabilities(2, Set.of("sys:role:grant")));
        verify(capabilities, never()).deleteByRoleId(2);
    }

    @Test
    void roleDeletionCleansEveryGrantTypeBeforeDeletingTheRole() {
        when(roles.lockById(2)).thenReturn(2);
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

    @Test
    void legacyTableSavePreservesHiddenColumnsOfRetainedTables() {
        holder.set(UserContextBo.builder().userId(1).superAdmin(true).build());
        when(roles.lockById(2)).thenReturn(2);
        TableInfo orderTable = new TableInfo();
        orderTable.setTableName("orders");
        when(tableMetadata.selectByDatasourceId(1)).thenReturn(List.of(orderTable));
        RoleHiddenColumn retained = new RoleHiddenColumn();
        retained.setTableName("orders");
        retained.setColumnName("phone");
        RoleHiddenColumn removed = new RoleHiddenColumn();
        removed.setTableName("removed");
        when(columns.selectByRoleAndDatasource(2, 1)).thenReturn(List.of(retained, removed));

        service.savePermissions(2, new SaveTablePermissionRequest(1, List.of("orders")));

        var order = inOrder(roles, tables, columns);
        order.verify(roles).lockById(2);
        order.verify(columns).selectByRoleAndDatasource(2, 1);
        order.verify(tables).deleteByRoleAndDatasource(2, 1);
        order.verify(columns).deleteByRoleAndDatasource(2, 1);
        order.verify(tables).insertTables(2, 1, List.of("orders"));
        order.verify(columns).insertColumns(List.of(retained));
    }

    @Test
    void legacyColumnSaveLeavesTableGrantsAndOtherTablesUntouched() {
        holder.set(UserContextBo.builder().userId(1).superAdmin(true).build());
        when(roles.lockById(2)).thenReturn(2);
        RoleTablePermission grant = new RoleTablePermission();
        grant.setTableName("orders");
        when(tables.selectByRoleAndDatasource(2, 1)).thenReturn(List.of(grant));
        ColumnInfo phone = new ColumnInfo();
        phone.setTableName("orders");
        phone.setColumnName("phone");
        when(columnMetadata.selectByDatasourceId(1)).thenReturn(List.of(phone));

        service.saveColumnPermissions(
                2, new SaveColumnPermissionRequest(1, "orders", List.of("phone")));

        verify(columns).deleteByRoleDatasourceAndTable(2, 1, "orders");
        verify(columns)
                .insertColumns(
                        argThat(
                                list ->
                                        list.size() == 1
                                                && list.get(0).getRoleId() == 2
                                                && list.get(0).getDatasourceId() == 1
                                                && list.get(0).getTableName().equals("orders")
                                                && list.get(0).getColumnName().equals("phone")));
        verify(tables, never()).deleteByRoleAndDatasource(any(), any());
        verify(columns, never()).deleteByRoleAndDatasource(any(), any());
        verify(tables, never()).insertTables(any(), any(), any());
    }

    @Test
    void legacyColumnSaveRejectsUngrantedTablesBeforeWriting() {
        holder.set(UserContextBo.builder().userId(1).superAdmin(true).build());
        when(roles.lockById(2)).thenReturn(2);
        assertThrows(
                BusinessException.class,
                () ->
                        service.saveColumnPermissions(
                                2, new SaveColumnPermissionRequest(1, "orders", List.of("phone"))));
        verifyNoInteractions(columns);
    }

    @Test
    void crossTableFailureRollsBackTheServiceTransaction() throws Exception {
        holder.set(UserContextBo.builder().userId(1).superAdmin(true).build());
        when(roles.lockById(2)).thenReturn(2);
        var dataSource = mock(javax.sql.DataSource.class);
        var connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(columns.deleteByRoleAndDatasource(2, 1))
                .thenAnswer(
                        invocation -> {
                            assertTrue(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            throw new IllegalStateException("Hidden-column write failed");
                        });
        var factory = new ProxyFactory(service);
        factory.addAdvice(
                new TransactionInterceptor(
                        new DataSourceTransactionManager(dataSource),
                        new AnnotationTransactionAttributeSource()));
        var transactionalService = (RolePermissionService) factory.getProxy();

        assertThrows(
                IllegalStateException.class,
                () ->
                        transactionalService.saveDataScope(
                                2, new RoleDataScopeDto(1, List.of(), Map.of())));

        verify(tables).deleteByRoleAndDatasource(2, 1);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}

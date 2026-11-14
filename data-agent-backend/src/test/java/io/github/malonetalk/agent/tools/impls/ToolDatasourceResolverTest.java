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
package io.github.malonetalk.agent.tools.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.agent.tools.ToolDatasourceResolver;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.PermissionService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolDatasourceResolverTest {

    private final PermissionService permissions = mock(PermissionService.class);
    private final DataPermissionService dataPermissions = mock(DataPermissionService.class);
    private final DatasourceService datasources = mock(DatasourceService.class);
    private final ToolDatasourceResolver resolver =
            new ToolDatasourceResolver(permissions, dataPermissions, datasources);

    private final UserContextBo user =
            UserContextBo.builder().userId(1).roleId(2).superAdmin(false).build();

    @Test
    void usesSoleDatasourceWhenIdOmitted() {
        DatasourcePo datasource = activeDatasource(1);
        when(permissions.user(1)).thenReturn(user);
        when(datasources.requireById(1)).thenReturn(datasource);

        DatasourcePo result = resolver.resolve(context(List.of(1)), null);

        assertSame(datasource, result);
        verify(dataPermissions).requireDatasource(user, 1);
    }

    @Test
    void requiresIdWhenMultipleDatasourcesExist() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> resolver.resolve(context(List.of(1, 2)), null));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void rejectsDatasourceOutsideAllowedList() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class, () -> resolver.resolve(context(List.of(1)), 2));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void rejectsInactiveDatasource() {
        DatasourcePo datasource = activeDatasource(1);
        datasource.setStatus(Status.INACTIVE.getCode());
        when(permissions.user(1)).thenReturn(user);
        when(datasources.requireById(1)).thenReturn(datasource);

        BusinessException exception =
                assertThrows(
                        BusinessException.class, () -> resolver.resolve(context(List.of(1)), 1));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void accessibleFiltersByCurrentPermissionAndStatus() {
        DatasourcePo one = activeDatasource(1);
        when(permissions.user(1)).thenReturn(user);
        when(dataPermissions.accessibleDatasourceIds(user)).thenReturn(Set.of(1));
        when(datasources.findActiveByIds(anyCollection())).thenReturn(List.of(one));

        List<DatasourcePo> result = resolver.accessible(context(List.of(1, 2)));

        assertEquals(List.of(one), result);
    }

    private ToolCallContext context(List<Integer> datasourceIds) {
        return ToolCallContext.builder().userId(1).datasourceIds(datasourceIds).build();
    }

    private DatasourcePo activeDatasource(Integer id) {
        DatasourcePo datasource = new DatasourcePo();
        datasource.setId(id);
        datasource.setName("ds-" + id);
        datasource.setStatus(Status.ACTIVE.getCode());
        return datasource;
    }
}

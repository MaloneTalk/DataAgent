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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.malonetalk.agent.datasource.DynamicDataSourceManager;
import io.github.malonetalk.agent.datasource.SqlExecutor;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.RoleCapabilityMapper;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.po.RoleHiddenColumn;
import io.github.malonetalk.model.po.RoleTablePermission;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlAuthorizationRegressionTest {
    private final Datasource datasource = new Datasource();
    private SqlExecutor executor;

    @BeforeEach
    void setup() {
        var users = mock(SysUserService.class);
        var capabilities = mock(RoleCapabilityMapper.class);
        var tables = mock(RoleTablePermissionMapper.class);
        var columns = mock(RoleHiddenColumnMapper.class);
        var metadata = mock(ColumnSemanticInfoMapper.class);
        when(users.selectAuthProjection(7))
                .thenReturn(UserContextBo.builder().userId(7).roleId(2).superAdmin(false).build());
        when(capabilities.selectCodes(2)).thenReturn(List.of("analysis:sql:execute"));
        var grant = new RoleTablePermission();
        grant.setTableName("orders");
        when(tables.selectByRoleAndDatasource(2, 1)).thenReturn(List.of(grant));
        var hidden = new RoleHiddenColumn();
        hidden.setTableName("orders");
        hidden.setColumnName("secret");
        when(columns.selectByRoleAndDatasource(2, 1)).thenReturn(List.of(hidden));
        var id = new ColumnInfo();
        id.setColumnName("id");
        when(metadata.selectByDatasourceIdAndTableName(1, "orders")).thenReturn(List.of(id));
        var permissions =
                new SqlPermissionService(
                        new PermissionService(users, capabilities),
                        new DataPermissionService(tables, columns),
                        metadata);
        executor = new SqlExecutor(mock(DynamicDataSourceManager.class), permissions);
        datasource.setId(1);
        datasource.setType("sqlite");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "SELECT id FROM orders LIMIT (SELECT secret FROM forbidden)",
                "SELECT id FROM orders OFFSET (SELECT secret FROM forbidden)",
                "SELECT id FROM orders LIMIT 1 OFFSET (SELECT secret FROM forbidden)",
                "SELECT id FROM orders FETCH FIRST (SELECT secret FROM forbidden) ROWS ONLY",
                "SELECT id FROM orders UNION SELECT id FROM orders LIMIT (SELECT secret FROM"
                        + " forbidden)",
                "(SELECT id FROM orders) LIMIT (SELECT secret FROM forbidden)",
                "SELECT * FROM (SELECT id FROM orders LIMIT (SELECT secret FROM forbidden)) nested",
                "SELECT id FROM orders LIMIT (SELECT secret FROM orders)",
                "SELECT SUM(id) OVER w FROM orders WINDOW w AS (ORDER BY (SELECT secret FROM"
                        + " forbidden))",
                "SELECT SUM(id) OVER (ORDER BY (SELECT secret FROM forbidden)) FROM orders",
                "SELECT SUM(id) FILTER (WHERE (SELECT secret FROM forbidden) > 0) OVER () FROM"
                        + " orders",
                "SELECT id FROM orders WINDOW w AS (ORDER BY (SELECT secret FROM forbidden))"
            })
    void rejectsUninspectedSubqueriesAndWindowExpressions(String sql) {
        var exception =
                assertThrows(
                        BusinessException.class,
                        () -> executor.authorizeSelectSql(datasource, sql, 7));
        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "SELECT id FROM orders LIMIT 2 OFFSET 1",
                "SELECT id FROM orders UNION SELECT id FROM orders LIMIT 2",
                "(SELECT id FROM orders) LIMIT 2",
                "SELECT id FROM orders FETCH FIRST 2 ROWS ONLY"
            })
    void preservesOrdinaryPagination(String sql) {
        assertTrue(
                executor.authorizeSelectSql(datasource, sql, 7)
                        .contains("SELECT \"id\" FROM \"orders\""));
    }

    @Test
    void authorizesAllowedSubqueriesInsidePagination() {
        String sql =
                executor.authorizeSelectSql(
                        datasource, "SELECT id FROM orders LIMIT (SELECT MAX(id) FROM orders)", 7);
        assertTrue(
                sql.contains("LIMIT (SELECT MAX(id) FROM (SELECT \"id\" FROM \"orders\") orders)"));
    }
}

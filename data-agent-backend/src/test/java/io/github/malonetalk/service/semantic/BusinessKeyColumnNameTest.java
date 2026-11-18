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
package io.github.malonetalk.service.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.malonetalk.convertor.SemanticConverter;
import io.github.malonetalk.dto.semantic.TableSemanticPageQuery;
import io.github.malonetalk.dto.semantic.TableSemanticUpdateRequest;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.bo.DataPermissionScopeBo;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.DatasourceWriteLockService;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import io.github.malonetalk.service.semantic.table.TableSemanticServiceImpl;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessKeyColumnNameTest {
    private static final UserContextBo USER = UserContextBo.builder().userId(1).build();
    private static final DataPermissionScopeBo SCOPE =
            new DataPermissionScopeBo(true, Map.of(), Map.of());

    @Mock private DatasourceService datasourceService;
    @Mock private DataPermissionService dataPermissions;
    @Mock private UserContextHolder holder;
    @Mock private TableInfoMapper tableInfoMapper;
    @Mock private ColumnSemanticInfoMapper columnMapper;
    @Mock private DatasourceWriteLockService datasourceLocks;
    @Mock private SemanticMergeService semanticMergeService;

    @Spy
    private SemanticConverter converter =
            new SemanticConverter(mock(LogicalTableRelationHelper.class));

    @InjectMocks private TableSemanticServiceImpl service;

    @BeforeEach
    void setUser() {
        when(holder.checkAndGet()).thenReturn(USER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\"b", "a`b"})
    void savesAndDistinguishesColumnNamesContainingQuotes(String quotedName) {
        TableInfo table = table();
        when(dataPermissions.loadScope(USER, 7)).thenReturn(SCOPE);
        when(tableInfoMapper.selectByDatasourceIdAndTableName(7, "orders")).thenReturn(table);
        when(columnMapper.selectByDatasourceIdAndTableName(7, "orders"))
                .thenReturn(List.of(column("ab"), column(quotedName)));
        var request =
                TableSemanticUpdateRequest.builder()
                        .datasourceId(7)
                        .tableName("orders")
                        .isVisible(true);

        service.updateTableSemantic(request.businessKeyColumns(List.of("ab")).build());
        assertEquals(List.of("ab"), table.getBusinessKeyColumns());

        service.updateTableSemantic(request.businessKeyColumns(List.of(quotedName)).build());
        assertEquals(List.of(quotedName), table.getBusinessKeyColumns());

        service.updateTableSemantic(
                request.businessKeyColumns(
                                List.of(" AB ", " " + quotedName.toUpperCase(Locale.ROOT) + " "))
                        .build());
        assertEquals(List.of("ab", quotedName), table.getBusinessKeyColumns());
        verify(tableInfoMapper, times(3)).updateSemanticFields(table);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\"b", "a`b"})
    void detectsMissingQuotedColumnsAndAcceptsDistinctPresentColumns(String quotedName) {
        TableInfo table = table();
        table.setBusinessKeyColumns(List.of(quotedName));
        when(datasourceService.findById(7)).thenReturn(Optional.of(new DatasourcePo()));
        when(dataPermissions.loadGrantedScope(USER, 7)).thenReturn(SCOPE);
        when(tableInfoMapper.selectPageByDatasourceId(any(), any(), anyBoolean(), any()))
                .thenReturn(new Page<TableInfo>(1, 20, 1).setRecords(List.of(table)));
        when(columnMapper.selectByDatasourceIdAndTableIds(7, Set.of(1)))
                .thenReturn(List.of(column("ab")), List.of(column("ab"), column(quotedName)));
        var query = new TableSemanticPageQuery(7, 1, 20, null, null);

        assertNotNull(service.getTablePage(query).items().get(0).businessKeyInvalidReason());

        table.setBusinessKeyColumns(List.of("ab", quotedName));
        var response = service.getTablePage(query).items().get(0);
        assertEquals(List.of("ab", quotedName), response.businessKeyColumns());
        assertNull(response.businessKeyInvalidReason());
    }

    private TableInfo table() {
        TableInfo table = new TableInfo();
        table.setId(1);
        table.setDatasourceId(7);
        table.setTableName("orders");
        table.setIsVisible(true);
        table.setPhysicalStatus(true);
        return table;
    }

    private ColumnInfo column(String name) {
        ColumnInfo column = new ColumnInfo();
        column.setTableId(1);
        column.setColumnName(name);
        column.setPhysicalStatus(true);
        return column;
    }
}

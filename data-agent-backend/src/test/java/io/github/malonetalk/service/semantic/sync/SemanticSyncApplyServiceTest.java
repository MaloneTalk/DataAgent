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
package io.github.malonetalk.service.semantic.sync;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService.ColumnSyncSource;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService.TableSyncSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SemanticSyncApplyServiceTest {

    @Mock private TableInfoMapper tableInfoMapper;
    @Mock private ColumnSemanticInfoMapper columnSemanticInfoMapper;
    @InjectMocks private SemanticSyncApplyService service;

    @Test
    void associatesNewColumnWithPersistedTableId() {
        TableInfo persistedTable = new TableInfo();
        persistedTable.setId(42);
        persistedTable.setDatasourceId(7);
        persistedTable.setTableName("orders");

        when(tableInfoMapper.selectByDatasourceIdAndTableNames(eq(7), anyList()))
                .thenReturn(List.of(), List.of(persistedTable));
        when(columnSemanticInfoMapper.selectByDatasourceIdAndTableNames(eq(7), anyList()))
                .thenReturn(List.of());

        service.applyTableSync(
                7,
                List.of(
                        new TableSyncSource(
                                "orders",
                                "orders table",
                                List.of(
                                        new ColumnSyncSource(
                                                "id", "order id", "BIGINT", true, "PRIMARY")))),
                List.of());

        verify(columnSemanticInfoMapper)
                .batchUpsertPhysicalCache(
                        org.mockito.ArgumentMatchers.argThat(
                                columns ->
                                        columns.size() == 1
                                                && Integer.valueOf(42)
                                                        .equals(columns.get(0).getTableId())));
    }
}

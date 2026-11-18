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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.malonetalk.agent.datasource.DynamicDataSourceManager;
import io.github.malonetalk.enums.DatasourceTestStatus;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.DatasourceMapper;
import io.github.malonetalk.model.converter.DatasourceConverter;
import io.github.malonetalk.model.dto.DatasourceDto;
import io.github.malonetalk.model.po.DatasourcePo;
import java.sql.SQLException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

class DatasourceServiceImplTest {
    private final DatasourceMapper mapper = mock(DatasourceMapper.class);
    private final DynamicDataSourceManager manager = mock(DynamicDataSourceManager.class);
    private final DatasourceServiceImpl service =
            new DatasourceServiceImpl(
                    mapper, manager, Mappers.getMapper(DatasourceConverter.class));
    private final DatasourceDto request =
            DatasourceDto.builder().name("test").type("mysql").host("localhost").port(3306).build();

    @Test
    void saveTestsBeforeInsertingSuccessfulStatus() throws Exception {
        when(manager.testConnection(any())).thenReturn(true);
        when(mapper.insert(any(DatasourcePo.class))).thenReturn(1);
        service.save(request);
        var order = inOrder(manager, mapper);
        order.verify(manager).testConnection(any());
        var inserted = ArgumentCaptor.forClass(DatasourcePo.class);
        order.verify(mapper).insert(inserted.capture());
        assertEquals("SUCCESS", inserted.getValue().getTestStatus());
    }

    @Test
    void unsuccessfulValidationCannotBeSaved() throws Exception {
        when(manager.testConnection(any())).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.save(request));
        verifyNoInteractions(mapper);
    }

    @Test
    void connectionExceptionCannotBeSaved() throws Exception {
        when(manager.testConnection(any())).thenThrow(new SQLException("unreachable"));
        assertThrows(BusinessException.class, () -> service.save(request));
        verifyNoInteractions(mapper);
    }

    @Test
    void editExplicitlyClearsPersistedTestStatus() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), DatasourcePo.class);
        DatasourcePo saved = new DatasourcePo();
        saved.setId(1);
        saved.setTestStatus("SUCCESS");
        when(mapper.selectById(1)).thenReturn(saved);
        when(mapper.update(any(DatasourcePo.class), any())).thenReturn(1);
        service.update(1, request);
        var wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(eq(saved), wrapper.capture());
        assertNull(saved.getTestStatus());
        assertTrue(wrapper.getValue().getSqlSet().contains("test_status="));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(null));
        verify(manager).removeDataSource(1);
    }

    @Test
    void testStatusUpdateDoesNotRewriteConnectionSettings() {
        when(mapper.updateById(any(DatasourcePo.class))).thenReturn(1);
        service.updateTestStatus(1, DatasourceTestStatus.FAILED);
        var update = ArgumentCaptor.forClass(DatasourcePo.class);
        verify(mapper).updateById(update.capture());
        assertEquals(1, update.getValue().getId());
        assertEquals("FAILED", update.getValue().getTestStatus());
        assertNull(update.getValue().getPassword());
        assertNull(update.getValue().getHost());
    }
}

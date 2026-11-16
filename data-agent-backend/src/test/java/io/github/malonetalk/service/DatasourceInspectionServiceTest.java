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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.malonetalk.agent.datasource.DynamicDataSourceManager;
import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.enums.DatasourceTestStatus;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.converter.DatasourceConverter;
import io.github.malonetalk.model.dto.DatasourceDto;
import io.github.malonetalk.model.po.DatasourcePo;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class DatasourceInspectionServiceTest {
    private final DatasourceService datasources = mock(DatasourceService.class);
    private final DynamicDataSourceManager manager = mock(DynamicDataSourceManager.class);
    private final DatasourceInspectionService service =
            new DatasourceInspectionService(
                    datasources,
                    manager,
                    mock(SchemaReader.class),
                    Mappers.getMapper(DatasourceConverter.class));
    private final DatasourcePo datasource = new DatasourcePo();

    @Test
    void savedTestPersistsSuccessAndFailure() throws Exception {
        when(datasources.requireById(1)).thenReturn(datasource);
        when(manager.testConnection(datasource)).thenReturn(true, false);
        assertTrue(service.testConnection(1).success());
        verify(datasources).updateTestStatus(1, DatasourceTestStatus.SUCCESS);
        assertFalse(service.testConnection(1).success());
        verify(datasources).updateTestStatus(1, DatasourceTestStatus.FAILED);
    }

    @Test
    void sqlFailurePersistsFailedStatus() throws Exception {
        when(datasources.requireById(1)).thenReturn(datasource);
        when(manager.testConnection(datasource)).thenThrow(new SQLException("unreachable"));
        assertFalse(service.testConnection(1).success());
        verify(datasources).updateTestStatus(1, DatasourceTestStatus.FAILED);
    }

    @Test
    void missingDriverPersistsFailureAndKeepsActionableError() throws Exception {
        when(datasources.requireById(1)).thenReturn(datasource);
        var failure = BusinessException.of(ErrorCode.JDBC_DRIVER_NOT_FOUND, "install driver");
        when(manager.testConnection(datasource)).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> service.testConnection(1)));
        verify(datasources).updateTestStatus(1, DatasourceTestStatus.FAILED);
    }

    @Test
    void unsavedTestDoesNotWriteStatus() {
        assertFalse(
                service.testConfiguration(DatasourceDto.builder().type("mysql").build()).success());
        verifyNoInteractions(datasources);
    }
}

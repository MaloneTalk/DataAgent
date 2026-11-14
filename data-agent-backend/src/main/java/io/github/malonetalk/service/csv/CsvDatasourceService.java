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
package io.github.malonetalk.service.csv;

import io.github.malonetalk.agent.datasource.DataSourceType;
import io.github.malonetalk.config.CsvImportConfig;
import io.github.malonetalk.enums.DatasourceSourceType;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.DatasourceMapper;
import io.github.malonetalk.model.po.DatasourcePo;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Shared validation of the configured CSV datasource for requests and background workers. */
@Service
@RequiredArgsConstructor
public class CsvDatasourceService {
    private final CsvImportConfig config;
    private final DatasourceMapper datasources;

    public DatasourcePo requireActive() {
        DatasourcePo datasource =
                Optional.ofNullable(datasources.selectById(config.getDatasourceId()))
                        .filter(
                                ds ->
                                        DatasourceSourceType.CSV.name().equals(ds.getSourceType())
                                                && DataSourceType.MYSQL
                                                        .getCode()
                                                        .equals(ds.getType()))
                        .filter(
                                ds ->
                                        ds.getDatabaseName() != null
                                                && !ds.getDatabaseName().isBlank())
                        .orElseThrow(
                                () ->
                                        BusinessException.of(
                                                ErrorCode.CSV_NOT_CONFIGURED,
                                                "CSV 导入尚未配置，请联系管理员设置 CSV_DATASOURCE_ID 和导入库连接。"));
        if (!Status.ACTIVE.getCode().equals(datasource.getStatus()))
            throw BusinessException.of(ErrorCode.CSV_DATASOURCE_DISABLED, "CSV 数据源已禁用。");
        return datasource;
    }
}

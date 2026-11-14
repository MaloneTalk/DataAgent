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

import io.github.malonetalk.agent.datasource.DynamicDataSourceManager;
import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.model.vo.DatasourceTableVo;
import io.github.malonetalk.model.vo.DatasourceTestVo;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceInspectionService {

    private final DatasourceService datasourceService;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final SchemaReader schemaReader;

    public DatasourceTestVo testConnection(Integer id) {
        DatasourcePo datasource = datasourceService.requireById(id);

        try {
            boolean success = dynamicDataSourceManager.testConnection(datasource);

            return new DatasourceTestVo(success, success ? "连接成功" : "连接验证失败，请检查数据库是否可用");
        } catch (BusinessException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            log.warn("Datasource connection test failed, datasourceId={}", id, e);

            return new DatasourceTestVo(false, "连接失败，请检查地址、端口、数据库名、账号密码及网络");
        }
    }

    public List<DatasourceTableVo> listPhysicalTables(Integer id) {
        DatasourcePo datasource = datasourceService.requireById(id);

        return schemaReader.getTables(datasource).stream()
                .map(
                        table ->
                                new DatasourceTableVo(
                                        table.tableName(), table.objectType(), table.remarks()))
                .sorted(
                        Comparator.comparing(
                                DatasourceTableVo::tableName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}

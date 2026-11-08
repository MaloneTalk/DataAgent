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

import static io.github.malonetalk.enums.CsvImportStatus.READY;
import static io.github.malonetalk.enums.CsvImportStatus.SYNCING;
import static io.github.malonetalk.enums.DatasourceSourceType.CSV;
import static io.github.malonetalk.utils.SemanticUtils.normalizeIdentifier;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.CsvImportMapper;
import io.github.malonetalk.model.bo.DataPermissionScopeBo;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.model.po.DatasourcePo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CsvAccessService {
    private final CsvImportMapper imports;

    public boolean managed(DatasourcePo datasource) {
        return CSV.name().equals(datasource.getSourceType());
    }

    public List<CsvImportPo> ready(DatasourcePo datasource) {
        return imports.selectList(
                Wrappers.<CsvImportPo>lambdaQuery()
                        .eq(CsvImportPo::getDatasourceId, datasource.getId())
                        .eq(CsvImportPo::getStatus, READY));
    }

    public DataPermissionScopeBo restrict(DatasourcePo datasource, DataPermissionScopeBo scope) {
        if (!managed(datasource)) return scope;
        Map<String, String> tables =
                ready(datasource).stream()
                        .filter(row -> scope.canTable(row.getPhysicalTableName()))
                        .collect(
                                Collectors.toMap(
                                        row -> normalizeIdentifier(row.getPhysicalTableName()),
                                        CsvImportPo::getPhysicalTableName));
        return new DataPermissionScopeBo(false, tables, scope.hiddenColumns());
    }

    public Predicate<String> readyFilter(DatasourcePo datasource) {
        if (!managed(datasource)) return table -> true;
        Set<String> names =
                ready(datasource).stream()
                        .map(row -> normalizeIdentifier(row.getPhysicalTableName()))
                        .collect(Collectors.toSet());
        return table -> names.contains(normalizeIdentifier(table));
    }

    public boolean isReady(DatasourcePo datasource, String table) {
        if (!managed(datasource)) return true;
        return imports.selectCount(
                        Wrappers.<CsvImportPo>lambdaQuery()
                                .eq(CsvImportPo::getDatasourceId, datasource.getId())
                                .eq(CsvImportPo::getPhysicalTableName, table)
                                .eq(CsvImportPo::getStatus, READY))
                > 0;
    }

    public void requireReady(DatasourcePo datasource, String table) {
        if (!isReady(datasource, table))
            throw BusinessException.of(ErrorCode.FORBIDDEN, "CSV 表尚未就绪或不属于当前数据源。");
    }

    public void requireImportTable(DatasourcePo datasource, CsvImportPo task) {
        if (!managed(datasource)
                || !datasource.getId().equals(task.getDatasourceId())
                || task.getStatus() != SYNCING
                || imports.selectCount(
                                Wrappers.<CsvImportPo>lambdaQuery()
                                        .eq(CsvImportPo::getId, task.getId())
                                        .eq(CsvImportPo::getAttemptId, task.getAttemptId())
                                        .eq(CsvImportPo::getStatus, task.getStatus())
                                        .eq(
                                                CsvImportPo::getPhysicalTableName,
                                                task.getPhysicalTableName()))
                        != 1) throw BusinessException.of(ErrorCode.BAD_REQUEST, "CSV 任务不能同步。");
    }
}

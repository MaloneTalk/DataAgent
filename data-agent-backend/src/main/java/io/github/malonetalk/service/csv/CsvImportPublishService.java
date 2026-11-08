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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.CsvImportMapper;
import io.github.malonetalk.model.converter.SemanticSyncConverter;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService.TableSyncSource;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CsvImportPublishService {
    private final CsvImportMapper imports;
    private final SchemaReader schemaReader;
    private final SemanticSyncApplyService semanticSync;
    private final SemanticSyncConverter converter;

    public TableSyncSource snapshot(CsvImportPo task, DatasourcePo datasource) {
        // Read the external database before opening the metadata publication transaction.
        var columns = schemaReader.getImportTableSchema(datasource, task);
        if (columns.isEmpty())
            throw BusinessException.of(ErrorCode.CSV_SCHEMA_INVALID, "导入表没有可同步的字段。");
        return converter.toCsvTable(task, columns);
    }

    @Transactional
    public void publish(CsvImportPo task, TableSyncSource snapshot) {
        Optional.ofNullable(
                        imports.selectOne(
                                Wrappers.<CsvImportPo>lambdaQuery()
                                        .eq(CsvImportPo::getId, task.getId())
                                        .last("FOR UPDATE")))
                .filter(
                        current ->
                                current.getStatus() == SYNCING
                                        && task.getAttemptId().equals(current.getAttemptId())
                                        && task.getPhysicalTableName()
                                                .equals(current.getPhysicalTableName()))
                .orElseThrow(
                        () ->
                                BusinessException.of(
                                        ErrorCode.CSV_TASK_STATE_CONFLICT, "CSV 任务状态已变化，请刷新后重试。"));
        // Semantic metadata and READY become visible in one business-database transaction.
        semanticSync.applyTableSync(task.getDatasourceId(), List.of(snapshot), List.of());
        imports.update(
                Wrappers.<CsvImportPo>lambdaUpdate()
                        .eq(CsvImportPo::getId, task.getId())
                        .eq(CsvImportPo::getAttemptId, task.getAttemptId())
                        .set(CsvImportPo::getStatus, READY)
                        .set(CsvImportPo::getErrorMessage, ""));
    }
}

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
import static io.github.malonetalk.model.converter.CsvSchemaConverter.invalid;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.mapper.CsvImportMapper;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService.ColumnSyncSource;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService.TableSyncSource;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CsvImportPublishService {
    private final CsvImportMapper imports;
    private final SchemaReader schemaReader;
    private final SemanticSyncApplyService semanticSync;

    public TableSyncSource snapshot(CsvImportPo task, DatasourcePo datasource) {
        List<ColumnSyncSource> columns =
                schemaReader.getImportTableSchema(datasource, task).stream()
                        .map(
                                column ->
                                        new ColumnSyncSource(
                                                column.columnName(),
                                                column.remarks(),
                                                column.formattedTypeName(),
                                                column.primaryKey(),
                                                column.formattedIndexInfo()))
                        .toList();
        if (columns.isEmpty()) throw invalid("导入表没有可同步的字段。");
        String description =
                task.getDescription() == null || task.getDescription().isBlank()
                        ? task.getDisplayName()
                        : task.getDisplayName() + "：" + task.getDescription();
        return new TableSyncSource(
                task.getPhysicalTableName(),
                description.substring(0, Math.min(500, description.length())),
                columns);
    }

    @Transactional
    public void publish(CsvImportPo task, TableSyncSource snapshot) {
        CsvImportPo current =
                imports.selectOne(
                        Wrappers.<CsvImportPo>lambdaQuery()
                                .eq(CsvImportPo::getId, task.getId())
                                .last("FOR UPDATE"));
        if (current == null || !Set.of(SYNCING).contains(current.getStatus()))
            throw invalid("CSV 任务状态已变化，请刷新后重试。");
        semanticSync.applyTableSync(task.getDatasourceId(), List.of(snapshot), List.of());
        imports.update(
                Wrappers.<CsvImportPo>lambdaUpdate()
                        .eq(CsvImportPo::getId, task.getId())
                        .set(CsvImportPo::getStatus, READY)
                        .set(CsvImportPo::getErrorMessage, ""));
    }
}

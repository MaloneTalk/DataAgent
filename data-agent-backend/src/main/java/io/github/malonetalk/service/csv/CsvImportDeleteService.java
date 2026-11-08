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

import static io.github.malonetalk.enums.CsvImportStatus.CLEANING;
import static io.github.malonetalk.enums.CsvImportStatus.DELETING;
import static io.github.malonetalk.enums.CsvImportStatus.IMPORTING;
import static io.github.malonetalk.enums.CsvImportStatus.QUEUED;
import static io.github.malonetalk.enums.CsvImportStatus.SYNCING;
import static io.github.malonetalk.model.converter.CsvSchemaConverter.invalid;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.mapper.CsvImportMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.MetricInfoMapper;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.service.MetricTableReferenceService;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CsvImportDeleteService {
    private final CsvImportMapper imports;
    private final TableInfoMapper tables;
    private final LogicalTableRelationMapper relations;
    private final MetricInfoMapper metrics;
    private final RoleTablePermissionMapper tablePermissions;
    private final RoleHiddenColumnMapper hiddenColumns;
    private final MetricTableReferenceService metricReferences;

    @Transactional
    public CsvImportPo begin(String id) {
        CsvImportPo task =
                Optional.ofNullable(
                                imports.selectOne(
                                        Wrappers.<CsvImportPo>lambdaQuery()
                                                .eq(CsvImportPo::getId, id)
                                                .last("FOR UPDATE")))
                        .orElseThrow(() -> invalid("导入记录不存在。"));
        if (Set.of(QUEUED, IMPORTING, SYNCING, CLEANING, DELETING).contains(task.getStatus()))
            throw invalid("任务正在运行，请完成后再删除。");
        requireUnreferenced(task);
        // Return the locked pre-delete snapshot for physical-table cleanup.
        imports.update(
                Wrappers.<CsvImportPo>lambdaUpdate()
                        .eq(CsvImportPo::getId, id)
                        .set(CsvImportPo::getStatus, DELETING));
        return task;
    }

    private void requireUnreferenced(CsvImportPo task) {
        var table =
                Optional.ofNullable(
                        tables.selectByDatasourceIdAndTableName(
                                task.getDatasourceId(), task.getPhysicalTableName()));
        if (table.isEmpty()) return;
        boolean linked =
                relations.selectByDatasourceId(task.getDatasourceId()).stream()
                        .anyMatch(
                                relation ->
                                        table.get().getId().equals(relation.getSourceTableId())
                                                || table.get()
                                                        .getId()
                                                        .equals(relation.getTargetTableId()));
        if (linked) throw invalid("该表仍有关联关系，请先删除关联关系。");
        Set<String> knownTables =
                tables.selectByDatasourceId(task.getDatasourceId()).stream()
                        .map(row -> SemanticUtils.normalizeIdentifier(row.getTableName()))
                        .collect(Collectors.toSet());
        boolean referenced =
                metrics.selectByDatasource(task.getDatasourceId()).stream()
                        .anyMatch(
                                metric ->
                                        metricReferences.mayReference(
                                                metric, task.getPhysicalTableName(), knownTables));
        if (referenced) throw invalid("该表存在指标引用，或数据源含无法确定归属的指标，请先调整指标口径。");
    }

    @Transactional
    public void finish(CsvImportPo task) {
        Optional.ofNullable(
                        imports.selectOne(
                                Wrappers.<CsvImportPo>lambdaQuery()
                                        .eq(CsvImportPo::getId, task.getId())
                                        .last("FOR UPDATE")))
                .filter(current -> current.getStatus() == DELETING)
                .orElseThrow(() -> invalid("删除任务状态已变化。"));
        tablePermissions.deleteImportedTable(task.getDatasourceId(), task.getPhysicalTableName());
        hiddenColumns.deleteImportedTable(task.getDatasourceId(), task.getPhysicalTableName());
        tables.deleteImportedTable(task.getDatasourceId(), task.getPhysicalTableName());
        imports.deleteById(task.getId());
    }
}

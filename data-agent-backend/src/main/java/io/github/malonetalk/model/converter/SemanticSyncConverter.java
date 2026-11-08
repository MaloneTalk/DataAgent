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
package io.github.malonetalk.model.converter;

import io.github.malonetalk.dto.datasource.PhysicalColumnInfo;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService.ColumnSyncSource;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService.TableSyncSource;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Builds the same physical schema snapshot for JDBC sync and CSV publication. */
@Component
public class SemanticSyncConverter {
    public TableSyncSource toTable(
            String name, String description, List<PhysicalColumnInfo> columns) {
        return new TableSyncSource(
                name,
                SemanticUtils.trimToNull(description),
                columns.stream().map(this::toColumn).toList());
    }

    public TableSyncSource toCsvTable(CsvImportPo task, List<PhysicalColumnInfo> columns) {
        String description =
                Optional.ofNullable(task.getDescription())
                        .filter(text -> !text.isBlank())
                        .map(text -> task.getDisplayName() + "：" + text)
                        .orElse(task.getDisplayName());
        return toTable(
                task.getPhysicalTableName(),
                description.substring(0, Math.min(500, description.length())),
                columns);
    }

    private ColumnSyncSource toColumn(PhysicalColumnInfo column) {
        return new ColumnSyncSource(
                SemanticUtils.requireTrimmed(column.columnName(), "Missing physical columnName."),
                SemanticUtils.trimToNull(column.remarks()),
                column.formattedTypeName(),
                column.primaryKey(),
                column.formattedIndexInfo());
    }
}

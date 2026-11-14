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
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.model.bo.ColumnSyncSourceBo;
import io.github.malonetalk.model.bo.TableSyncSourceBo;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import java.util.Optional;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Builds the same physical schema snapshot for JDBC sync and CSV publication. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class SemanticSyncConverter {
    // Business defaults and persistence identities are assigned by the applying service.
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "datasourceId", source = "datasourceId")
    @Mapping(target = "tableName", source = "table.tableName")
    @Mapping(target = "objectType", source = "table.objectType")
    @Mapping(target = "physicalTableDescription", source = "table.description")
    @Mapping(target = "tableDescription", source = "table.description")
    public abstract TableInfo toTableInfo(Integer datasourceId, TableSyncSourceBo table);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "datasourceId", source = "datasourceId")
    @Mapping(target = "tableId", source = "tableId")
    @Mapping(target = "columnName", source = "column.columnName")
    @Mapping(target = "physicalColumnDescription", source = "column.description")
    @Mapping(target = "columnDescription", source = "column.description")
    @Mapping(target = "typeName", source = "column.typeName")
    @Mapping(target = "primaryKey", source = "column.primaryKey")
    @Mapping(target = "indexInfo", source = "column.indexInfo")
    public abstract ColumnInfo toColumnInfo(
            Integer datasourceId, Integer tableId, ColumnSyncSourceBo column);

    public TableSyncSourceBo toTable(
            String name, String description, String objectType, List<PhysicalColumnInfo> columns) {
        return new TableSyncSourceBo(
                name,
                SemanticUtils.trimToNull(description),
                objectType,
                columns.stream().map(this::toColumn).toList());
    }

    public TableSyncSourceBo toCsvTable(CsvImportPo task, List<PhysicalColumnInfo> columns) {
        String description =
                Optional.ofNullable(task.getDescription())
                        .filter(text -> !text.isBlank())
                        .map(text -> task.getDisplayName() + "：" + text)
                        .orElse(task.getDisplayName());
        return toTable(
                task.getPhysicalTableName(),
                description.substring(0, Math.min(500, description.length())),
                "TABLE",
                columns);
    }

    private ColumnSyncSourceBo toColumn(PhysicalColumnInfo column) {
        return new ColumnSyncSourceBo(
                SemanticUtils.requireTrimmed(column.columnName(), "Missing physical columnName."),
                SemanticUtils.trimToNull(column.remarks()),
                column.formattedTypeName(),
                column.primaryKey(),
                column.formattedIndexInfo());
    }
}

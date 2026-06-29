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
package io.github.malonetalk.convertor;

import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.dto.semantic.ColumnSemanticResponse;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.TableSemanticResponse;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.enums.LogicalTableRelationType;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SemanticConverter {

    private final LogicalTableRelationHelper logicalTableRelationHelper;

    public TableSemanticResponse toResponse(TableInfo tableInfo) {
        Boolean isVisible = tableInfo.getIsVisible();
        return TableSemanticResponse.builder()
                .id(tableInfo.getId())
                .tableName(tableInfo.getTableName())
                .domain(SemanticUtils.normalizeDomain(tableInfo.getDomain()))
                .tableDescription(SemanticUtils.trimToNull(tableInfo.getTableDescription()))
                .isVisible(isVisible)
                .hasPhysicalTable(true)
                .effective(Boolean.TRUE.equals(isVisible))
                .updateTime(tableInfo.getUpdateTime())
                .build();
    }

    public ColumnSemanticResponse toResponse(ColumnInfo columnInfo) {
        return ColumnSemanticResponse.builder()
                .id(columnInfo.getId())
                .columnName(columnInfo.getColumnName())
                .columnDescription(columnInfo.getColumnDescription())
                .isVisible(columnInfo.getIsVisible())
                .hasPhysicalColumn(true)
                .effective(Boolean.TRUE.equals(columnInfo.getIsVisible()))
                .updateTime(columnInfo.getUpdateTime())
                .build();
    }

    public LogicalTableRelationResponse toResponse(LogicalTableRelation relation) {
        List<String> sourceColumns =
                logicalTableRelationHelper.fromJson(
                        relation.getSourceColumnNamesJson(), "sourceColumnNames");
        List<String> targetColumns =
                logicalTableRelationHelper.fromJson(
                        relation.getTargetColumnNamesJson(), "targetColumnNames");
        String relationKey =
                logicalTableRelationHelper.buildRelationKey(
                        relation.getSourceTableName(),
                        sourceColumns,
                        relation.getTargetTableName(),
                        targetColumns);
        return LogicalTableRelationResponse.builder()
                .id(relation.getId())
                .relationKey(relationKey)
                .datasourceId(relation.getDatasourceId())
                .source(SemanticConstants.RELATION_SOURCE_LOGICAL)
                .sourceTableName(relation.getSourceTableName())
                .sourceColumnNames(sourceColumns)
                .targetTableName(relation.getTargetTableName())
                .targetColumnNames(targetColumns)
                .relationType(LogicalTableRelationType.fromCode(relation.getRelationType()))
                .description(relation.getDescription())
                .enabled(relation.getIsEnabled())
                .createTime(relation.getCreateTime())
                .updateTime(relation.getUpdateTime())
                .build();
    }
}

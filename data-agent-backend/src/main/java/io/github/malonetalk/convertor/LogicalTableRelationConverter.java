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

import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogicalTableRelationConverter {

    private final LogicalTableRelationHelper logicalTableRelationHelper;

    public LogicalTableRelationResponse toResponse(LogicalTableRelation relation) {
        List<String> sourceColumns =
                logicalTableRelationHelper.fromJson(
                        relation.getSourceColumnNamesJson(), "sourceColumnNames");
        List<String> targetColumns =
                logicalTableRelationHelper.fromJson(
                        relation.getTargetColumnNamesJson(), "targetColumnNames");
        return new LogicalTableRelationResponse(
                relation.getId(),
                logicalTableRelationHelper.buildRelationKey(
                        relation.getSourceTableName(),
                        sourceColumns,
                        relation.getTargetTableName(),
                        targetColumns),
                relation.getDatasourceId(),
                LogicalTableRelationHelper.RELATION_SOURCE_LOGICAL,
                relation.getSourceTableName(),
                sourceColumns,
                relation.getTargetTableName(),
                targetColumns,
                relation.getRelationType(),
                relation.getDescription(),
                relation.getIsEnabled(),
                relation.getIsEnabled(),
                null,
                relation.getCreateTime(),
                relation.getUpdateTime());
    }
}

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
package io.github.malonetalk.service.semantic.relation.impl;

import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import io.github.malonetalk.entity.RelationState;
import io.github.malonetalk.dto.semantic.RelationValidationRequest;
import io.github.malonetalk.entity.ResolvedColumn;
import io.github.malonetalk.entity.ResolvedTable;
import io.github.malonetalk.entity.SemanticContext;
import io.github.malonetalk.service.semantic.relation.RelationSemanticPolicyService;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RelationSemanticPolicyServiceImpl implements RelationSemanticPolicyService {

    private static final Logger logger =
            LoggerFactory.getLogger(RelationSemanticPolicyServiceImpl.class);

    private final LogicalTableRelationHelper logicalTableRelationHelper;

    public RelationSemanticPolicyServiceImpl(
            LogicalTableRelationHelper logicalTableRelationHelper) {
        this.logicalTableRelationHelper = logicalTableRelationHelper;
    }

    @Override
    public List<Integer> normalizeRelationIds(List<Integer> relationIds, String fieldName) {
        if (relationIds == null || relationIds.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
        for (Integer relationId : relationIds) {
            if (relationId == null || relationId <= 0) {
                throw new IllegalArgumentException(
                        fieldName + " contains an invalid relation id: " + relationId);
            }
        }
        return relationIds.stream().distinct().toList();
    }

    @Override
    public String normalizeKeywordPrefix(String keywordPrefix) {
        if (keywordPrefix == null || keywordPrefix.isBlank()) {
            return null;
        }
        return keywordPrefix.trim();
    }

    @Override
    public ResolvedTable requireVisibleTable(
            SemanticContext readContext, String tableName, String roleLabel) {
        ResolvedTable table = readContext.findTable(tableName);
        if (table == null || !table.hasPhysicalTable()) {
            throw new IllegalArgumentException(roleLabel + " does not exist: " + tableName);
        }
        if (!table.visible()) {
            throw new IllegalArgumentException(roleLabel + " is not visible: " + tableName);
        }
        return table;
    }

    @Override
    public void requireVisibleColumns(
            SemanticContext readContext,
            String tableName,
            List<String> columnNames,
            String roleLabel) {
        for (String columnName : columnNames) {
            ResolvedColumn column = readContext.findColumn(tableName, columnName);
            if (column == null || !column.hasPhysicalColumn()) {
                throw new IllegalArgumentException(
                        roleLabel + " column does not exist: " + tableName + "." + columnName);
            }
            if (!column.visible()) {
                throw new IllegalArgumentException(
                        roleLabel + " column is not visible: " + tableName + "." + columnName);
            }
        }
    }

    @Override
    public RelationDraft buildDraft(
            SemanticContext readContext,
            String tableName,
            List<String> sourceColumnNames,
            String targetTableName,
            List<String> targetColumnNames,
            String description,
            Boolean enabled) {
        String normalizedSourceTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        List<String> normalizedSourceColumns =
                logicalTableRelationHelper.normalizeColumnNames(
                        sourceColumnNames, "sourceColumnNames");
        String normalizedTargetTableName =
                logicalTableRelationHelper.normalizeTableName(targetTableName, "targetTableName");
        List<String> normalizedTargetColumns =
                logicalTableRelationHelper.normalizeColumnNames(
                        targetColumnNames, "targetColumnNames");
        if (normalizedSourceColumns.size() != normalizedTargetColumns.size()) {
            throw new IllegalArgumentException(
                    "Source columns and target columns must have the same size.");
        }

        ResolvedTable sourceTable =
                requireVisibleTable(readContext, normalizedSourceTableName, "source table");
        ResolvedTable targetTable =
                requireVisibleTable(readContext, normalizedTargetTableName, "target table");
        String canonicalSourceTableName = sourceTable.canonicalName();
        String canonicalTargetTableName = targetTable.canonicalName();
        requireVisibleColumns(readContext, canonicalSourceTableName, normalizedSourceColumns, "source");
        requireVisibleColumns(readContext, canonicalTargetTableName, normalizedTargetColumns, "target");

        return new RelationDraft(
                canonicalSourceTableName,
                logicalTableRelationHelper.toJson(normalizedSourceColumns),
                logicalTableRelationHelper.buildColumnSignature(normalizedSourceColumns),
                canonicalTargetTableName,
                logicalTableRelationHelper.toJson(normalizedTargetColumns),
                logicalTableRelationHelper.buildColumnSignature(normalizedTargetColumns),
                logicalTableRelationHelper.normalizeDescription(description),
                enabled);
    }

    @Override
    public LogicalTableRelationResponse mapResponse(
            SemanticContext readContext, LogicalTableRelation relation) {
        ColumnDecodeResult sourceColumnResult =
                decodeColumnNames(
                        relation.getId(), relation.getSourceColumnNamesJson(), "sourceColumnNames");
        ColumnDecodeResult targetColumnResult =
                decodeColumnNames(
                        relation.getId(), relation.getTargetColumnNamesJson(), "targetColumnNames");
        RelationState relationState =
                readContext.evaluateRelationState(
                        new RelationValidationRequest(
                                relation.getSourceTableName(),
                                sourceColumnResult.columnNames(),
                                relation.getTargetTableName(),
                                targetColumnResult.columnNames(),
                                Boolean.TRUE.equals(relation.getIsEnabled()),
                                sourceColumnResult.errorMessage(),
                                targetColumnResult.errorMessage()));
        return new LogicalTableRelationResponse(
                relation.getId(),
                relation.getDatasourceId(),
                relation.getSourceTableName(),
                sourceColumnResult.columnNames(),
                relation.getTargetTableName(),
                targetColumnResult.columnNames(),
                relation.getRelationType(),
                relation.getDescription(),
                relation.getIsEnabled(),
                relationState.effective(),
                relationState.invalidReason(),
                relation.getCreateTime(),
                relation.getUpdateTime());
    }

    private ColumnDecodeResult decodeColumnNames(
            Integer relationId, String columnNamesJson, String fieldName) {
        try {
            return new ColumnDecodeResult(
                    logicalTableRelationHelper.fromJson(columnNamesJson, fieldName), null);
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Failed to decode {} for relation {}: {}",
                    fieldName,
                    relationId,
                    e.getMessage());
            return new ColumnDecodeResult(
                    Collections.emptyList(),
                    "Failed to decode " + fieldName + " for relation " + relationId + ".");
        }
    }

    private record ColumnDecodeResult(List<String> columnNames, String errorMessage) {}
}

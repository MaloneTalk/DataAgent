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
package io.github.malonetalk.service.semantic.relation;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.BindLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationRequest;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RelationSemanticServiceImpl implements RelationSemanticService {

    private final DatasourceService datasourceService;
    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final LogicalTableRelationHelper logicalTableRelationHelper;

    @Override
    public PageResponse<LogicalTableRelationResponse> getRelationPage(
            Integer datasourceId,
            String tableName,
            PageRequest pageRequest,
            String keywordPrefix,
            Boolean enabled,
            String sortOrder) {
        requireDatasource(datasourceId);
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        SemanticUtils.validateSortOrder(sortOrder);
        String normalizedPrefix = SemanticUtils.normalizeBlankToNull(keywordPrefix);
        boolean sortDescending = SemanticConstants.SORT_ORDER_DESC.equalsIgnoreCase(sortOrder);
        PageHelper.startPage(pageRequest.page(), pageRequest.pageSize());
        Page<LogicalTableRelation> page =
                (Page<LogicalTableRelation>)
                        logicalTableRelationMapper.selectPageByDatasourceIdAndSourceTable(
                                datasourceId,
                                normalizedTableName,
                                normalizedPrefix,
                                enabled,
                                sortDescending);
        if (page.getTotal() == 0L) {
            return PageResponse.empty(pageRequest);
        }
        List<LogicalTableRelationResponse> items = page.stream().map(this::mapResponse).toList();
        return PageResponse.of(items, page.getTotal(), pageRequest);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse createRelationSemantic(
            Integer datasourceId, String tableName, BindLogicalTableRelationRequest request) {
        requireDatasource(datasourceId);
        LogicalTableRelation relation = buildRelation(datasourceId, tableName, request);
        ensureUniqueSourceKey(
                datasourceId,
                relation.getSourceTableName(),
                relation.getSourceColumnSignature(),
                null);
        logicalTableRelationMapper.insert(relation);
        return mapResponse(relation);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse updateRelationSemantic(
            Integer datasourceId,
            String tableName,
            Integer relationId,
            UpdateLogicalTableRelationRequest request) {
        requireDatasource(datasourceId);
        LogicalTableRelation existing = requireRelation(datasourceId, tableName, relationId);
        applyRelationUpdate(existing, tableName, request);
        ensureUniqueSourceKey(
                datasourceId,
                existing.getSourceTableName(),
                existing.getSourceColumnSignature(),
                existing.getId());
        existing.setUpdateTime(LocalDateTime.now());
        logicalTableRelationMapper.update(existing);
        return mapResponse(existing);
    }

    @Override
    @Transactional
    public boolean updateRelationSemanticEnabled(
            Integer datasourceId, String tableName, Integer relationId, Boolean enabled) {
        requireDatasource(datasourceId);
        if (enabled == null) {
            throw new IllegalArgumentException("enabled cannot be null.");
        }
        LogicalTableRelation relation = requireRelation(datasourceId, tableName, relationId);
        return logicalTableRelationMapper.updateEnabled(
                        relationId,
                        datasourceId,
                        relation.getSourceTableName(),
                        enabled,
                        LocalDateTime.now())
                > 0;
    }

    @Override
    @Transactional
    public boolean deleteRelationSemantic(
            Integer datasourceId, String tableName, Integer relationId) {
        requireDatasource(datasourceId);
        LogicalTableRelation relation = requireRelation(datasourceId, tableName, relationId);
        return logicalTableRelationMapper.deleteById(
                        relationId, datasourceId, relation.getSourceTableName())
                > 0;
    }

    @Override
    @Transactional
    public int deleteRelationSemantics(
            Integer datasourceId, String tableName, List<Integer> relationIds) {
        requireDatasource(datasourceId);
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        if (relationIds == null || relationIds.isEmpty()) {
            return 0;
        }
        List<Integer> matchedIds =
                logicalTableRelationMapper
                        .selectByDatasourceIdAndSourceTable(datasourceId, normalizedTableName)
                        .stream()
                        .map(LogicalTableRelation::getId)
                        .filter(relationIds::contains)
                        .distinct()
                        .toList();
        if (matchedIds.size() != relationIds.size()) {
            throw new IllegalArgumentException(
                    "Some logical relations do not exist or do not belong to source table "
                            + normalizedTableName
                            + ".");
        }
        return logicalTableRelationMapper.deleteByIdsAndSourceTable(
                datasourceId, normalizedTableName, relationIds);
    }

    private void requireDatasource(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
    }

    private LogicalTableRelation buildRelation(
            Integer datasourceId, String tableName, BindLogicalTableRelationRequest request) {
        LogicalTableRelation relation = new LogicalTableRelation();
        relation.setDatasourceId(datasourceId);
        relation.setSourceTableName(
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName"));
        relation.setSourceColumnNamesJson(
                logicalTableRelationHelper.toJson(request.sourceColumnNames()));
        relation.setSourceColumnSignature(
                logicalTableRelationHelper.buildColumnSignature(request.sourceColumnNames()));
        relation.setTargetTableName(
                logicalTableRelationHelper.normalizeTableName(
                        request.targetTableName(), "targetTableName"));
        relation.setTargetColumnNamesJson(
                logicalTableRelationHelper.toJson(request.targetColumnNames()));
        relation.setTargetColumnSignature(
                logicalTableRelationHelper.buildColumnSignature(request.targetColumnNames()));
        relation.setRelationType(LogicalTableRelationHelper.RELATION_TYPE_FOREIGN_KEY);
        relation.setDescription(
                logicalTableRelationHelper.normalizeDescription(request.description()));
        relation.setIsEnabled(request.enabled());
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        return relation;
    }

    private void applyRelationUpdate(
            LogicalTableRelation relation,
            String tableName,
            UpdateLogicalTableRelationRequest request) {
        relation.setSourceTableName(
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName"));
        relation.setSourceColumnNamesJson(
                logicalTableRelationHelper.toJson(request.sourceColumnNames()));
        relation.setSourceColumnSignature(
                logicalTableRelationHelper.buildColumnSignature(request.sourceColumnNames()));
        relation.setTargetTableName(
                logicalTableRelationHelper.normalizeTableName(
                        request.targetTableName(), "targetTableName"));
        relation.setTargetColumnNamesJson(
                logicalTableRelationHelper.toJson(request.targetColumnNames()));
        relation.setTargetColumnSignature(
                logicalTableRelationHelper.buildColumnSignature(request.targetColumnNames()));
        relation.setRelationType(LogicalTableRelationHelper.RELATION_TYPE_FOREIGN_KEY);
        relation.setDescription(
                logicalTableRelationHelper.normalizeDescription(request.description()));
        relation.setIsEnabled(request.enabled());
    }

    private void ensureUniqueSourceKey(
            Integer datasourceId,
            String sourceTableName,
            String sourceColumnSignature,
            Integer currentRelationId) {
        LogicalTableRelation existing =
                logicalTableRelationMapper.selectByUniqueSourceKey(
                        datasourceId, sourceTableName, sourceColumnSignature);
        if (existing == null) {
            return;
        }
        if (currentRelationId != null && currentRelationId.equals(existing.getId())) {
            return;
        }
        throw new IllegalArgumentException(
                "A logical relation already exists for the same source columns.");
    }

    private LogicalTableRelation requireRelation(
            Integer datasourceId, String tableName, Integer relationId) {
        if (relationId == null) {
            throw new IllegalArgumentException("relationId cannot be null.");
        }
        LogicalTableRelation relation = logicalTableRelationMapper.selectById(relationId);
        if (relation == null
                || !datasourceId.equals(relation.getDatasourceId())
                || !logicalTableRelationHelper.sameTableName(
                        relation.getSourceTableName(), tableName)) {
            throw new IllegalArgumentException("Logical relation does not exist.");
        }
        return relation;
    }

    private LogicalTableRelationResponse mapResponse(LogicalTableRelation relation) {
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

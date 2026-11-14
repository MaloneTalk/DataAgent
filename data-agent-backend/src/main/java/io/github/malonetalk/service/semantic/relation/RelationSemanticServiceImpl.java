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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.malonetalk.convertor.SemanticConverter;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.BindLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.RelationSemanticPageQuery;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationEnabledRequest;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationRequest;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.enums.LogicalTableRelationType;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.bo.DataPermissionScopeBo;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.DatasourceWriteLockService;
import io.github.malonetalk.service.semantic.SemanticAvailabilityHelper;
import io.github.malonetalk.utils.RequestAssert;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RelationSemanticServiceImpl implements RelationSemanticService {

    private final DatasourceService datasourceService;
    private final DataPermissionService dataPermissions;
    private final UserContextHolder holder;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final LogicalTableRelationHelper logicalTableRelationHelper;
    private final SemanticConverter semanticConverter;
    private final DatasourceWriteLockService datasourceLocks;

    @Override
    public PageResponse<LogicalTableRelationResponse> getRelationPage(
            RelationSemanticPageQuery query) {
        requireDatasource(query.datasourceId());
        String normalizedTableName =
                SemanticUtils.normalizeObjectName(query.tableName(), "tableName");
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        boolean sortDescending = SemanticUtils.isDescendingSort(query.sortOrder());
        IPage<LogicalTableRelation> page =
                logicalTableRelationMapper.selectPageByDatasourceIdAndSourceTable(
                        new Page<>(pageNumber, pageSize),
                        new RelationSemanticPageQuery(
                                query.datasourceId(),
                                normalizedTableName,
                                pageNumber,
                                pageSize,
                                SemanticUtils.trimToNull(query.keyword()),
                                query.enabled(),
                                query.sortOrder()),
                        sortDescending,
                        dataPermissions.roleScope(holder.checkAndGet()).orElse(null));
        if (page.getTotal() == 0L) {
            return PageResponse.empty(pageNumber, pageSize);
        }
        List<LogicalTableRelationResponse> items =
                page.getRecords().stream().map(semanticConverter::toResponse).toList();
        return PageResponse.of(items, page.getTotal(), pageNumber, pageSize);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse createRelationSemantic(
            String tableName, BindLogicalTableRelationRequest request) {
        datasourceLocks.lock(request.datasourceId());
        // Load CSV READY visibility only after taking the same lock as import deletion.
        var scope = dataPermissions.loadScope(holder.checkAndGet(), request.datasourceId());
        LogicalTableRelation relation = buildRelation(tableName, request, scope);
        logicalTableRelationMapper.insert(relation);
        return semanticConverter.toResponse(relation);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse updateRelationSemantic(
            String tableName, UpdateLogicalTableRelationRequest request) {
        datasourceLocks.lock(request.datasourceId());
        var scope = dataPermissions.loadScope(holder.checkAndGet(), request.datasourceId());
        LogicalTableRelation existing =
                requireRelation(request.datasourceId(), tableName, request.relationId(), scope);
        applyRelationUpdate(existing, tableName, request, scope);
        existing.setUpdateTime(LocalDateTime.now());
        logicalTableRelationMapper.update(existing);
        return semanticConverter.toResponse(existing);
    }

    @Override
    @Transactional
    public boolean updateRelationSemanticEnabled(
            String tableName, UpdateLogicalTableRelationEnabledRequest request) {
        datasourceLocks.lock(request.datasourceId());
        RequestAssert.requireNonNull(request.enabled(), "enabled cannot be null.");
        var scope = dataPermissions.loadScope(holder.checkAndGet(), request.datasourceId());
        LogicalTableRelation relation =
                requireRelation(request.datasourceId(), tableName, request.relationId(), scope);
        if (Boolean.TRUE.equals(request.enabled())) {
            ensureRelationEndpointsOperable(relation, scope);
        }
        return logicalTableRelationMapper.updateEnabled(
                        request.relationId(),
                        request.datasourceId(),
                        relation.getSourceTableId(),
                        request.enabled(),
                        LocalDateTime.now())
                > 0;
    }

    @Override
    @Transactional
    public boolean deleteRelationSemantic(
            Integer datasourceId, String tableName, Integer relationId) {
        requireDatasource(datasourceId);
        var scope = dataPermissions.loadScope(holder.checkAndGet(), datasourceId);
        LogicalTableRelation relation = requireRelation(datasourceId, tableName, relationId, scope);
        return logicalTableRelationMapper.deleteById(
                        relationId, datasourceId, relation.getSourceTableId())
                > 0;
    }

    @Override
    @Transactional
    public int deleteRelationSemantics(
            Integer datasourceId, String tableName, List<Integer> relationIds) {
        requireDatasource(datasourceId);
        String normalizedTableName = SemanticUtils.normalizeObjectName(tableName, "tableName");
        if (relationIds == null || relationIds.isEmpty()) {
            return 0;
        }
        var scope = dataPermissions.loadScope(holder.checkAndGet(), datasourceId);
        TableInfo sourceTable =
                requireTable(datasourceId, normalizedTableName, "sourceTable", scope);
        relationIds.forEach(id -> requireRelation(datasourceId, normalizedTableName, id, scope));
        int deleted =
                logicalTableRelationMapper.deleteByIdsAndSourceTable(
                        datasourceId, sourceTable.getId(), relationIds);
        if (deleted != relationIds.size()) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Some logical relations do not exist or do not belong to source table "
                            + normalizedTableName
                            + ".");
        }
        return deleted;
    }

    private void requireDatasource(Integer datasourceId) {
        RequestAssert.requireNonNull(datasourceId, "datasourceId cannot be null.");
        if (datasourceService.findById(datasourceId).isEmpty()) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND, "Datasource does not exist: " + datasourceId);
        }
    }

    private LogicalTableRelation buildRelation(
            String tableName,
            BindLogicalTableRelationRequest request,
            DataPermissionScopeBo scope) {
        LogicalTableRelation relation = new LogicalTableRelation();
        relation.setDatasourceId(request.datasourceId());
        populateRelationFields(
                relation,
                tableName,
                request.sourceColumnNames(),
                request.targetColumnNames(),
                request.targetTableName(),
                request.relationType(),
                request.description(),
                request.enabled());
        ensureRelationEndpointsOperable(relation, scope);
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        return relation;
    }

    private void applyRelationUpdate(
            LogicalTableRelation relation,
            String tableName,
            UpdateLogicalTableRelationRequest request,
            DataPermissionScopeBo scope) {
        populateRelationFields(
                relation,
                tableName,
                request.sourceColumnNames(),
                request.targetColumnNames(),
                request.targetTableName(),
                request.relationType(),
                request.description(),
                request.enabled());
        ensureRelationEndpointsOperable(relation, scope);
    }

    private void ensureRelationEndpointsOperable(
            LogicalTableRelation relation, DataPermissionScopeBo scope) {
        if (!logicalTableRelationHelper.isVisible(scope, relation)) {
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        TableInfo sourceTable =
                ensureTableOperable(
                        relation.getDatasourceId(),
                        relation.getSourceTableName(),
                        "sourceTable",
                        scope);
        TableInfo targetTable =
                ensureTableOperable(
                        relation.getDatasourceId(),
                        relation.getTargetTableName(),
                        "targetTable",
                        scope);
        relation.setSourceTableId(sourceTable.getId());
        relation.setTargetTableId(targetTable.getId());
        List<String> sourceColumns =
                logicalTableRelationHelper.fromJson(
                        relation.getSourceColumnNamesJson(), "sourceColumnNames");
        List<String> targetColumns =
                logicalTableRelationHelper.fromJson(
                        relation.getTargetColumnNamesJson(), "targetColumnNames");
        var tableIds =
                Stream.of(sourceTable.getId(), targetTable.getId()).collect(Collectors.toSet());
        Map<Integer, Map<String, ColumnInfo>> columnsByTableId =
                columnSemanticInfoMapper
                        .selectByDatasourceIdAndTableIds(relation.getDatasourceId(), tableIds)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        ColumnInfo::getTableId,
                                        Collectors.toMap(
                                                c -> c.getColumnName().toLowerCase(Locale.ROOT),
                                                c -> c)));
        ensureColumnsOperable(
                columnsByTableId.getOrDefault(sourceTable.getId(), Map.of()),
                sourceColumns,
                "sourceColumnNames");
        ensureColumnsOperable(
                columnsByTableId.getOrDefault(targetTable.getId(), Map.of()),
                targetColumns,
                "targetColumnNames");
    }

    private TableInfo ensureTableOperable(
            Integer datasourceId, String tableName, String fieldName, DataPermissionScopeBo scope) {
        TableInfo tableInfo = requireTable(datasourceId, tableName, fieldName, scope);
        if (SemanticAvailabilityHelper.isTableAvailable(tableInfo)) {
            return tableInfo;
        }
        throw BusinessException.of(
                ErrorCode.DATA_CONFLICT,
                SemanticAvailabilityHelper.unavailableMessage(
                        fieldName,
                        tableName,
                        SemanticAvailabilityHelper.tableInvalidReason(tableInfo)));
    }

    private TableInfo requireTable(
            Integer datasourceId, String tableName, String fieldName, DataPermissionScopeBo scope) {
        scope.requireTable(tableName);
        return Optional.ofNullable(
                        tableInfoMapper.selectByDatasourceIdAndTableName(datasourceId, tableName))
                .orElseThrow(
                        () ->
                                BusinessException.of(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        fieldName
                                                + " "
                                                + tableName
                                                + " semantic metadata does not exist."));
    }

    private void ensureColumnsOperable(
            Map<String, ColumnInfo> columns, List<String> columnNames, String fieldName) {
        for (String columnName : columnNames) {
            ColumnInfo columnInfo =
                    Optional.ofNullable(columns.get(columnName.toLowerCase(Locale.ROOT)))
                            .orElseThrow(
                                    () ->
                                            BusinessException.of(
                                                    ErrorCode.RESOURCE_NOT_FOUND,
                                                    fieldName
                                                            + " "
                                                            + columnName
                                                            + " semantic metadata does not"
                                                            + " exist."));
            if (SemanticAvailabilityHelper.isColumnAvailable(columnInfo)) {
                continue;
            }
            throw BusinessException.of(
                    ErrorCode.DATA_CONFLICT,
                    SemanticAvailabilityHelper.unavailableMessage(
                            fieldName,
                            columnName,
                            SemanticAvailabilityHelper.columnInvalidReason(columnInfo)));
        }
    }

    private void populateRelationFields(
            LogicalTableRelation relation,
            String tableName,
            List<String> sourceColumnNames,
            List<String> targetColumnNames,
            String targetTableName,
            LogicalTableRelationType relationType,
            String description,
            Boolean enabled) {
        relation.setSourceTableName(SemanticUtils.normalizeObjectName(tableName, "tableName"));
        relation.setSourceColumnNamesJson(
                logicalTableRelationHelper.toJson(sourceColumnNames, "sourceColumnNames"));
        relation.setTargetTableName(
                SemanticUtils.normalizeObjectName(targetTableName, "targetTableName"));
        relation.setTargetColumnNamesJson(
                logicalTableRelationHelper.toJson(targetColumnNames, "targetColumnNames"));
        LogicalTableRelationType resolvedRelationType =
                relationType == null ? LogicalTableRelationType.FOREIGN_KEY : relationType;
        relation.setRelationType(resolvedRelationType.getCode());
        relation.setDescription(SemanticUtils.trimToNull(description));
        relation.setIsEnabled(enabled);
    }

    private LogicalTableRelation requireRelation(
            Integer datasourceId,
            String tableName,
            Integer relationId,
            DataPermissionScopeBo scope) {
        RequestAssert.requireNonNull(relationId, "relationId cannot be null.");
        String normalizedTableName = SemanticUtils.normalizeObjectName(tableName, "tableName");
        LogicalTableRelation relation = logicalTableRelationMapper.selectById(relationId);
        // selectById 已关联 table_info，直接用当前来源表名验证归属。
        if (relation == null
                || !datasourceId.equals(relation.getDatasourceId())
                || !normalizedTableName.equals(
                        SemanticUtils.normalizeObjectName(
                                relation.getSourceTableName(), "sourceTableName"))) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND, "Logical relation does not exist.");
        }
        if (!logicalTableRelationHelper.isVisible(scope, relation)) {
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        return relation;
    }
}

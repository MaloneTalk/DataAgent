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
import io.github.malonetalk.convertor.SemanticConverter;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.BindLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.RelationSemanticPageQuery;
import io.github.malonetalk.dto.semantic.RelationWorkspacePageQuery;
import io.github.malonetalk.dto.semantic.RelationWorkspaceResponse;
import io.github.malonetalk.dto.semantic.RelationWorkspaceTableResponse;
import io.github.malonetalk.dto.semantic.TableSemanticPageQuery;
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
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.SemanticAvailabilityHelper;
import io.github.malonetalk.utils.RequestAssert;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RelationSemanticServiceImpl implements RelationSemanticService {

    private final DatasourceService datasourceService;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final LogicalTableRelationHelper logicalTableRelationHelper;
    private final SemanticConverter semanticConverter;

    @Override
    public PageResponse<LogicalTableRelationResponse> getRelationPage(
            RelationSemanticPageQuery query) {
        requireDatasource(query.datasourceId());
        String normalizedTableName =
                SemanticUtils.normalizeObjectName(query.tableName(), "tableName");
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        boolean sortDescending = SemanticUtils.isDescendingSort(query.sortOrder());
        PageHelper.startPage(pageNumber, pageSize);
        Page<LogicalTableRelation> page =
                (Page<LogicalTableRelation>)
                        logicalTableRelationMapper.selectPageByDatasourceIdAndSourceTable(
                                new RelationSemanticPageQuery(
                                        query.datasourceId(),
                                        normalizedTableName,
                                        pageNumber,
                                        pageSize,
                                        SemanticUtils.trimToNull(query.keyword()),
                                        query.enabled(),
                                        query.sortOrder()),
                                sortDescending);
        if (page.getTotal() == 0L) {
            return PageResponse.empty(pageNumber, pageSize);
        }
        List<LogicalTableRelationResponse> items =
                page.stream().map(semanticConverter::toResponse).toList();
        return PageResponse.of(items, page.getTotal(), pageNumber, pageSize);
    }

    @Override
    public RelationWorkspaceResponse getRelationWorkspace(RelationWorkspacePageQuery query) {
        requireDatasource(query.datasourceId());
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        boolean sortDescending = SemanticUtils.isDescendingSort(query.sortOrder());

        PageHelper.startPage(pageNumber, pageSize);
        Page<TableInfo> page =
                (Page<TableInfo>)
                        tableInfoMapper.selectPageByDatasourceId(
                                new TableSemanticPageQuery(
                                        query.datasourceId(),
                                        pageNumber,
                                        pageSize,
                                        SemanticUtils.trimToNull(query.keyword()),
                                        query.sortOrder()),
                                sortDescending);
        if (page.isEmpty()) {
            return new RelationWorkspaceResponse(
                    PageResponse.empty(pageNumber, pageSize), List.of());
        }

        Set<Integer> currentPageTableIds =
                page.stream().map(TableInfo::getId).collect(Collectors.toSet());
        // 列记录已持有 table_id，按主键分组可直接关联当前页的表。
        Map<Integer, List<ColumnInfo>> columnsByTableId =
                columnSemanticInfoMapper
                        .selectByDatasourceIdAndTableIds(query.datasourceId(), currentPageTableIds)
                        .stream()
                        .collect(Collectors.groupingBy(ColumnInfo::getTableId));
        List<RelationWorkspaceTableResponse> nodes =
                page.stream()
                        .map(
                                table ->
                                        semanticConverter.toWorkspaceTable(
                                                table,
                                                columnsByTableId.getOrDefault(
                                                        table.getId(), List.of())))
                        .toList();
        // 来源表来自当前页查询，目标表也必须属于当前页。
        List<LogicalTableRelationResponse> relations =
                logicalTableRelationMapper
                        .selectByDatasourceIdAndSourceTableIds(
                                query.datasourceId(), currentPageTableIds)
                        .stream()
                        .filter(
                                relation ->
                                        currentPageTableIds.contains(relation.getTargetTableId()))
                        .map(semanticConverter::toResponse)
                        .toList();

        return new RelationWorkspaceResponse(
                PageResponse.of(nodes, page.getTotal(), pageNumber, pageSize), relations);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse createRelationSemantic(
            String tableName, BindLogicalTableRelationRequest request) {
        requireDatasource(request.datasourceId());
        LogicalTableRelation relation = buildRelation(request.datasourceId(), tableName, request);
        logicalTableRelationMapper.insert(relation);
        return semanticConverter.toResponse(relation);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse updateRelationSemantic(
            String tableName, UpdateLogicalTableRelationRequest request) {
        requireDatasource(request.datasourceId());
        LogicalTableRelation existing =
                requireRelation(request.datasourceId(), tableName, request.relationId());
        applyRelationUpdate(existing, tableName, request);
        existing.setUpdateTime(LocalDateTime.now());
        logicalTableRelationMapper.update(existing);
        return semanticConverter.toResponse(existing);
    }

    @Override
    @Transactional
    public boolean updateRelationSemanticEnabled(
            String tableName, UpdateLogicalTableRelationEnabledRequest request) {
        requireDatasource(request.datasourceId());
        RequestAssert.requireNonNull(request.enabled(), "enabled cannot be null.");
        LogicalTableRelation relation =
                requireRelation(request.datasourceId(), tableName, request.relationId());
        if (Boolean.TRUE.equals(request.enabled())) {
            ensureRelationEndpointsOperable(relation);
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
        LogicalTableRelation relation = requireRelation(datasourceId, tableName, relationId);
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
        TableInfo sourceTable = requireTable(datasourceId, normalizedTableName, "sourceTable");
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
        if (datasourceService.findById(datasourceId) == null) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND, "Datasource does not exist: " + datasourceId);
        }
    }

    private LogicalTableRelation buildRelation(
            Integer datasourceId, String tableName, BindLogicalTableRelationRequest request) {
        LogicalTableRelation relation = new LogicalTableRelation();
        relation.setDatasourceId(datasourceId);
        populateRelationFields(
                relation,
                tableName,
                request.sourceColumnNames(),
                request.targetColumnNames(),
                request.targetTableName(),
                request.relationType(),
                request.description(),
                request.enabled());
        ensureRelationEndpointsOperable(relation);
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        return relation;
    }

    private void applyRelationUpdate(
            LogicalTableRelation relation,
            String tableName,
            UpdateLogicalTableRelationRequest request) {
        populateRelationFields(
                relation,
                tableName,
                request.sourceColumnNames(),
                request.targetColumnNames(),
                request.targetTableName(),
                request.relationType(),
                request.description(),
                request.enabled());
        ensureRelationEndpointsOperable(relation);
    }

    private void ensureRelationEndpointsOperable(LogicalTableRelation relation) {
        TableInfo sourceTable =
                ensureTableOperable(
                        relation.getDatasourceId(), relation.getSourceTableName(), "sourceTable");
        TableInfo targetTable =
                ensureTableOperable(
                        relation.getDatasourceId(), relation.getTargetTableName(), "targetTable");
        relation.setSourceTableId(sourceTable.getId());
        relation.setTargetTableId(targetTable.getId());
        List<String> sourceColumns =
                logicalTableRelationHelper.fromJson(
                        relation.getSourceColumnNamesJson(), "sourceColumnNames");
        List<String> targetColumns =
                logicalTableRelationHelper.fromJson(
                        relation.getTargetColumnNamesJson(), "targetColumnNames");
        ensureColumnsOperable(
                relation.getDatasourceId(),
                relation.getSourceTableName(),
                sourceColumns,
                "sourceColumnNames");
        ensureColumnsOperable(
                relation.getDatasourceId(),
                relation.getTargetTableName(),
                targetColumns,
                "targetColumnNames");
    }

    private TableInfo ensureTableOperable(
            Integer datasourceId, String tableName, String fieldName) {
        TableInfo tableInfo = requireTable(datasourceId, tableName, fieldName);
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

    private TableInfo requireTable(Integer datasourceId, String tableName, String fieldName) {
        TableInfo tableInfo =
                tableInfoMapper.selectByDatasourceIdAndTableName(datasourceId, tableName);
        if (tableInfo == null) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    fieldName + " " + tableName + " semantic metadata does not exist.");
        }
        return tableInfo;
    }

    private void ensureColumnsOperable(
            Integer datasourceId, String tableName, List<String> columnNames, String fieldName) {
        for (String columnName : columnNames) {
            ColumnInfo columnInfo =
                    columnSemanticInfoMapper.selectByDatasourceIdAndTableNameAndColumnName(
                            datasourceId, tableName, columnName);
            if (columnInfo == null) {
                throw BusinessException.of(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        fieldName + " " + columnName + " semantic metadata does not exist.");
            }
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
            Integer datasourceId, String tableName, Integer relationId) {
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
        return relation;
    }
}

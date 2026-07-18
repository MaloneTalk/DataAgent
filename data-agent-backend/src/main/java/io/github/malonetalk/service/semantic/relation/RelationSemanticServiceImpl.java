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
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.SemanticAvailabilityHelper;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
                logicalTableRelationHelper.normalizeTableName(
                        query.tableName(), "Missing tableName for logical relation page query.");
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

        List<String> tableNames = page.stream().map(TableInfo::getTableName).distinct().toList();
        Set<String> currentPageTableNames =
                tableNames.stream()
                        .map(
                                tableName ->
                                        SemanticUtils.normalizeObjectName(
                                                tableName,
                                                "Missing tableName while building relation"
                                                        + " workspace page."))
                        .collect(Collectors.toSet());
        Map<String, List<ColumnInfo>> columnsByTableName =
                columnSemanticInfoMapper
                        .selectByDatasourceIdAndTableNames(query.datasourceId(), tableNames)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        column ->
                                                SemanticUtils.normalizeObjectName(
                                                        column.getTableName(),
                                                        "Missing tableName while grouping relation"
                                                                + " workspace columns."),
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        List<RelationWorkspaceTableResponse> nodes =
                page.stream()
                        .map(
                                table ->
                                        semanticConverter.toWorkspaceTable(
                                                table,
                                                columnsByTableName.getOrDefault(
                                                        SemanticUtils.normalizeObjectName(
                                                                table.getTableName(),
                                                                "Missing tableName while mapping"
                                                                        + " relation workspace"
                                                                        + " table."),
                                                        List.of())))
                        .toList();
        List<LogicalTableRelationResponse> relations =
                logicalTableRelationMapper
                        .selectByDatasourceIdAndSourceTables(query.datasourceId(), tableNames)
                        .stream()
                        .filter(
                                relation ->
                                        currentPageTableNames.contains(
                                                SemanticUtils.normalizeObjectName(
                                                        relation.getTargetTableName(),
                                                        "Missing targetTableName while filtering"
                                                                + " relation workspace"
                                                                + " relations.")))
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
        ensureUniqueSourceKey(
                request.datasourceId(),
                relation.getSourceTableName(),
                relation.getSourceColumnSignature(),
                null);
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
        ensureUniqueSourceKey(
                request.datasourceId(),
                existing.getSourceTableName(),
                existing.getSourceColumnSignature(),
                existing.getId());
        existing.setUpdateTime(LocalDateTime.now());
        logicalTableRelationMapper.update(existing);
        return semanticConverter.toResponse(existing);
    }

    @Override
    @Transactional
    public boolean updateRelationSemanticEnabled(
            String tableName, UpdateLogicalTableRelationEnabledRequest request) {
        requireDatasource(request.datasourceId());
        if (request.enabled() == null) {
            throw new IllegalArgumentException("enabled cannot be null.");
        }
        LogicalTableRelation relation =
                requireRelation(request.datasourceId(), tableName, request.relationId());
        if (Boolean.TRUE.equals(request.enabled())) {
            ensureRelationOperandsOperable(relation);
        }
        return logicalTableRelationMapper.updateEnabled(
                        request.relationId(),
                        request.datasourceId(),
                        relation.getSourceTableName(),
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
                        relationId, datasourceId, relation.getSourceTableName())
                > 0;
    }

    @Override
    @Transactional
    public int deleteRelationSemantics(
            Integer datasourceId, String tableName, List<Integer> relationIds) {
        requireDatasource(datasourceId);
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(
                        tableName, "Missing tableName for batch logical relation delete.");
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
        populateRelationFields(
                relation,
                tableName,
                request.sourceColumnNames(),
                request.targetColumnNames(),
                request.targetTableName(),
                request.description(),
                request.enabled());
        ensureRelationOperandsOperable(relation);
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
                request.description(),
                request.enabled());
        ensureRelationOperandsOperable(relation);
    }

    private void ensureRelationOperandsOperable(LogicalTableRelation relation) {
        ensureTableOperable(
                relation.getDatasourceId(), relation.getSourceTableName(), "sourceTable");
        ensureTableOperable(
                relation.getDatasourceId(), relation.getTargetTableName(), "targetTable");
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

    private void ensureTableOperable(Integer datasourceId, String tableName, String fieldName) {
        TableInfo tableInfo =
                tableInfoMapper.selectByDatasourceIdAndTableName(datasourceId, tableName);
        if (tableInfo == null) {
            throw new IllegalArgumentException(
                    fieldName + " " + tableName + " semantic metadata does not exist.");
        }
        if (!SemanticAvailabilityHelper.isUnavailable(
                tableInfo, SemanticAvailabilityHelper.UsageLevel.USER_OPERATION)) {
            return;
        }
        throw new IllegalArgumentException(
                SemanticAvailabilityHelper.unavailableMessage(
                        fieldName,
                        tableName,
                        SemanticAvailabilityHelper.tableInvalidReason(
                                tableInfo, SemanticAvailabilityHelper.UsageLevel.USER_OPERATION)));
    }

    private void ensureColumnsOperable(
            Integer datasourceId, String tableName, List<String> columnNames, String fieldName) {
        for (String columnName : columnNames) {
            ColumnInfo columnInfo =
                    columnSemanticInfoMapper.selectByDatasourceIdAndTableNameAndColumnName(
                            datasourceId, tableName, columnName);
            if (columnInfo == null) {
                throw new IllegalArgumentException(
                        fieldName + " " + columnName + " semantic metadata does not exist.");
            }
            if (!SemanticAvailabilityHelper.isUnavailable(
                    columnInfo, SemanticAvailabilityHelper.UsageLevel.USER_OPERATION)) {
                continue;
            }
            throw new IllegalArgumentException(
                    SemanticAvailabilityHelper.unavailableMessage(
                            fieldName,
                            columnName,
                            SemanticAvailabilityHelper.columnInvalidReason(
                                    columnInfo,
                                    SemanticAvailabilityHelper.UsageLevel.USER_OPERATION)));
        }
    }

    private void populateRelationFields(
            LogicalTableRelation relation,
            String tableName,
            List<String> sourceColumnNames,
            List<String> targetColumnNames,
            String targetTableName,
            String description,
            Boolean enabled) {
        relation.setSourceTableName(
                logicalTableRelationHelper.normalizeTableName(
                        tableName, "Missing source tableName for logical relation save."));
        List<String> normalizedSourceColumns =
                logicalTableRelationHelper.normalizeColumnNames(
                        sourceColumnNames, "sourceColumnNames");
        relation.setSourceColumnNamesJson(
                logicalTableRelationHelper.toJson(normalizedSourceColumns));
        relation.setSourceColumnSignature(
                logicalTableRelationHelper.buildColumnSignature(normalizedSourceColumns));
        relation.setTargetTableName(
                logicalTableRelationHelper.normalizeTableName(
                        targetTableName, "Missing targetTableName for logical relation save."));
        List<String> normalizedTargetColumns =
                logicalTableRelationHelper.normalizeColumnNames(
                        targetColumnNames, "targetColumnNames");
        relation.setTargetColumnNamesJson(
                logicalTableRelationHelper.toJson(normalizedTargetColumns));
        relation.setTargetColumnSignature(
                logicalTableRelationHelper.buildColumnSignature(normalizedTargetColumns));
        relation.setRelationType(SemanticConstants.RELATION_TYPE_FOREIGN_KEY);
        relation.setDescription(SemanticUtils.trimToNull(description));
        relation.setIsEnabled(enabled);
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
                || !relation.getSourceTableName()
                        .equals(
                                SemanticUtils.normalizeObjectName(
                                        tableName,
                                        "Missing tableName for logical relation lookup."))) {
            throw new IllegalArgumentException("Logical relation does not exist.");
        }
        return relation;
    }
}

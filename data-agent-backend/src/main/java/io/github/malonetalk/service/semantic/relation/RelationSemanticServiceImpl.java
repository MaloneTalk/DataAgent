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
import io.github.malonetalk.convertor.LogicalTableRelationConverter;
import io.github.malonetalk.dto.PageResponse;
import io.github.malonetalk.dto.semantic.BindLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.ColumnSemanticPageQuery;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateColumnResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateTableResponse;
import io.github.malonetalk.dto.semantic.RelationSemanticPageQuery;
import io.github.malonetalk.dto.semantic.TableSemanticPageQuery;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationEnabledRequest;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationRequest;
import io.github.malonetalk.entity.Column;
import io.github.malonetalk.entity.ColumnSemantic;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.TableSemantic;
import io.github.malonetalk.exception.SemanticSchemaException;
import io.github.malonetalk.infrastructure.SchemaReader;
import io.github.malonetalk.mapper.ColumnSemanticMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.TableSemanticMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationSemanticServiceImpl implements RelationSemanticService {

    private final DatasourceService datasourceService;
    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final LogicalTableRelationHelper logicalTableRelationHelper;
    private final LogicalTableRelationConverter logicalTableRelationConverter;
    private final TableSemanticMapper tableSemanticMapper;
    private final ColumnSemanticMapper columnSemanticMapper;
    private final SchemaReader schemaReader;

    @Override
    public PageResponse<LogicalTableRelationResponse> getRelationPage(
            RelationSemanticPageQuery query) {
        requireDatasource(query.datasourceId());
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(query.tableName(), "tableName");
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        SemanticUtils.validateSortOrder(query.sortOrder());
        boolean sortDescending =
                SemanticConstants.SORT_ORDER_DESC.equalsIgnoreCase(query.sortOrder());
        PageHelper.startPage(pageNumber, pageSize);
        Page<LogicalTableRelation> page =
                (Page<LogicalTableRelation>)
                        logicalTableRelationMapper.selectPageByDatasourceIdAndSourceTable(
                                new RelationSemanticPageQuery(
                                        query.datasourceId(),
                                        normalizedTableName,
                                        pageNumber,
                                        pageSize,
                                        SemanticUtils.normalizeBlankToNull(query.keyword()),
                                        query.enabled(),
                                        query.sortOrder()),
                                sortDescending);
        if (page.getTotal() == 0L) {
            return PageResponse.empty(pageNumber, pageSize);
        }
        List<LogicalTableRelationResponse> items =
                page.stream().map(logicalTableRelationConverter::toResponse).toList();
        return PageResponse.of(items, page.getTotal(), pageNumber, pageSize);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse createRelationSemantic(
            String tableName, BindLogicalTableRelationRequest request) {
        requireDatasource(request.datasourceId());
        validateEndpoints(
                request.datasourceId(),
                tableName,
                request.sourceColumnNames(),
                request.targetTableName(),
                request.targetColumnNames());
        LogicalTableRelation relation = buildRelation(request.datasourceId(), tableName, request);
        ensureUniqueSourceKey(
                request.datasourceId(),
                relation.getSourceTableName(),
                relation.getSourceColumnSignature(),
                null);
        logicalTableRelationMapper.insert(relation);
        return logicalTableRelationConverter.toResponse(relation);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse updateRelationSemantic(
            String tableName, UpdateLogicalTableRelationRequest request) {
        requireDatasource(request.datasourceId());
        validateEndpoints(
                request.datasourceId(),
                tableName,
                request.sourceColumnNames(),
                request.targetTableName(),
                request.targetColumnNames());
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
        return logicalTableRelationConverter.toResponse(existing);
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

    @Override
    public PageResponse<RelationCandidateTableResponse> getCandidateTablePage(
            RelationSemanticPageQuery query) {
        requireDatasource(query.datasourceId());
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        SemanticUtils.validateSortOrder(query.sortOrder());
        boolean sortDescending =
                SemanticConstants.SORT_ORDER_DESC.equalsIgnoreCase(query.sortOrder());
        PageHelper.startPage(pageNumber, pageSize);
        Page<TableSemantic> page =
                (Page<TableSemantic>)
                        tableSemanticMapper.selectVisiblePageByDatasourceId(
                                new TableSemanticPageQuery(
                                        query.datasourceId(),
                                        pageNumber,
                                        pageSize,
                                        SemanticUtils.normalizeBlankToNull(query.keyword()),
                                        query.sortOrder()),
                                sortDescending);
        List<RelationCandidateTableResponse> items =
                page.stream()
                        .map(
                                t ->
                                        new RelationCandidateTableResponse(
                                                t.getTableName(),
                                                SemanticUtils.normalizeBlankToNull(t.getDomain()),
                                                SemanticUtils.normalizeBlankToNull(
                                                        t.getTableDescription())))
                        .toList();
        return PageResponse.of(items, page.getTotal(), pageNumber, pageSize);
    }

    @Override
    public PageResponse<RelationCandidateColumnResponse> getCandidateColumnPage(
            RelationSemanticPageQuery query) {
        requireDatasource(query.datasourceId());
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(query.tableName(), "tableName");
        TableSemantic table =
                tableSemanticMapper.selectByDatasourceIdAndTableName(
                        query.datasourceId(), normalizedTableName);
        if (table == null || !Boolean.TRUE.equals(table.getIsVisible())) {
            throw new IllegalArgumentException("表 " + normalizedTableName + " 不存在或不可见。");
        }
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        SemanticUtils.validateSortOrder(query.sortOrder());
        boolean sortDescending =
                SemanticConstants.SORT_ORDER_DESC.equalsIgnoreCase(query.sortOrder());
        PageHelper.startPage(pageNumber, pageSize);
        Page<ColumnSemantic> page =
                (Page<ColumnSemantic>)
                        columnSemanticMapper.selectVisiblePageByDatasourceIdAndTableName(
                                new ColumnSemanticPageQuery(
                                        query.datasourceId(),
                                        normalizedTableName,
                                        pageNumber,
                                        pageSize,
                                        SemanticUtils.normalizeBlankToNull(query.keyword()),
                                        query.sortOrder()),
                                sortDescending);

        // 物理列信息补充
        Map<String, Column> physicalByKey = new HashMap<>();
        try {
            for (Column col :
                    schemaReader.getTableSchema(
                            datasourceService.findById(query.datasourceId()),
                            normalizedTableName)) {
                physicalByKey.put(col.columnName().toLowerCase(Locale.ROOT), col);
            }
        } catch (SchemaReader.SchemaReadException e) {
            log.warn("无法读取表 {} 的物理列信息: {}", normalizedTableName, e.getMessage());
        }

        List<RelationCandidateColumnResponse> items =
                page.stream()
                        .map(
                                c -> {
                                    Column phys =
                                            physicalByKey.get(
                                                    c.getColumnName().toLowerCase(Locale.ROOT));
                                    return new RelationCandidateColumnResponse(
                                            c.getColumnName(),
                                            SemanticUtils.normalizeBlankToNull(
                                                    c.getColumnDescription()),
                                            phys != null ? buildTypeText(phys) : null,
                                            phys != null ? phys.primaryKey() : null);
                                })
                        .toList();
        return PageResponse.of(items, page.getTotal(), pageNumber, pageSize);
    }

    private static String buildTypeText(Column physicalColumn) {
        StringBuilder sb = new StringBuilder(physicalColumn.typeName());
        if (physicalColumn.columnSize() > 0) {
            sb.append("(").append(physicalColumn.columnSize()).append(")");
        }
        return sb.toString();
    }

    private void requireDatasource(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
    }

    private void validateEndpoints(
            Integer datasourceId,
            String sourceTableName,
            List<String> sourceColumnNames,
            String targetTableName,
            List<String> targetColumnNames) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
        String normalizedSourceTableName =
                logicalTableRelationHelper.normalizeTableName(sourceTableName, "sourceTableName");
        String normalizedTargetTableName =
                logicalTableRelationHelper.normalizeTableName(targetTableName, "targetTableName");
        List<String> normalizedSourceColumnNames =
                logicalTableRelationHelper.normalizeColumnNames(
                        sourceColumnNames, "sourceColumnNames");
        List<String> normalizedTargetColumnNames =
                logicalTableRelationHelper.normalizeColumnNames(
                        targetColumnNames, "targetColumnNames");
        TableSemantic sourceTable =
                tableSemanticMapper.selectByDatasourceIdAndTableName(
                        datasourceId, normalizedSourceTableName);
        if (sourceTable == null || !Boolean.TRUE.equals(sourceTable.getIsVisible())) {
            throw new IllegalArgumentException("源表 " + normalizedSourceTableName + " 不存在或不可见。");
        }
        TableSemantic targetTable =
                tableSemanticMapper.selectByDatasourceIdAndTableName(
                        datasourceId, normalizedTargetTableName);
        if (targetTable == null || !Boolean.TRUE.equals(targetTable.getIsVisible())) {
            throw new IllegalArgumentException("目标表 " + normalizedTargetTableName + " 不存在或不可见。");
        }
        for (String columnName : normalizedSourceColumnNames) {
            ColumnSemantic col =
                    columnSemanticMapper.selectByDatasourceIdAndTableNameAndColumnName(
                            datasourceId, normalizedSourceTableName, columnName);
            if (col == null || !Boolean.TRUE.equals(col.getIsVisible())) {
                throw new IllegalArgumentException(
                        "源列 " + normalizedSourceTableName + "." + columnName + " 不存在或不可见。");
            }
        }
        for (String columnName : normalizedTargetColumnNames) {
            ColumnSemantic col =
                    columnSemanticMapper.selectByDatasourceIdAndTableNameAndColumnName(
                            datasourceId, normalizedTargetTableName, columnName);
            if (col == null || !Boolean.TRUE.equals(col.getIsVisible())) {
                throw new IllegalArgumentException(
                        "目标列 " + normalizedTargetTableName + "." + columnName + " 不存在或不可见。");
            }
        }

        validatePhysicalSchema(
                datasource,
                normalizedSourceTableName,
                normalizedSourceColumnNames,
                normalizedTargetTableName,
                normalizedTargetColumnNames);
    }

    private void validatePhysicalSchema(
            Datasource datasource,
            String sourceTableName,
            List<String> sourceColumnNames,
            String targetTableName,
            List<String> targetColumnNames) {
        Map<String, Column> sourceSchema = loadSchema(datasource, sourceTableName);
        Map<String, Column> targetSchema = loadSchema(datasource, targetTableName);
        if (sourceSchema.isEmpty()) {
            throw new SemanticSchemaException(
                    "Physical source table does not exist: " + sourceTableName);
        }
        if (targetSchema.isEmpty()) {
            throw new SemanticSchemaException(
                    "Physical target table does not exist: " + targetTableName);
        }
        for (String columnName : sourceColumnNames) {
            if (!sourceSchema.containsKey(columnName.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Physical source column does not exist: "
                                + sourceTableName
                                + "."
                                + columnName);
            }
        }
        for (String columnName : targetColumnNames) {
            if (!targetSchema.containsKey(columnName.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Physical target column does not exist: "
                                + targetTableName
                                + "."
                                + columnName);
            }
        }
    }

    private Map<String, Column> loadSchema(Datasource datasource, String tableName) {
        Map<String, Column> physicalByKey = new HashMap<>();
        try {
            for (Column column : schemaReader.getTableSchema(datasource, tableName)) {
                physicalByKey.put(column.columnName().toLowerCase(Locale.ROOT), column);
            }
        } catch (SchemaReader.SchemaReadException e) {
            throw new SemanticSchemaException(
                    "Failed to read physical schema for table " + tableName, e);
        }
        return physicalByKey;
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
}

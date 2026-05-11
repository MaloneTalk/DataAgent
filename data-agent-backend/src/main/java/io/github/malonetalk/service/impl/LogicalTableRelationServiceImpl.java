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
package io.github.malonetalk.service.impl;

import io.github.malonetalk.agent.datasource.ColumnInfo;
import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.BindLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateColumnResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateTableResponse;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationRequest;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.LogicalTableRelationService;
import io.github.malonetalk.service.LogicalTableRelationSupport;
import io.github.malonetalk.service.SemanticVisibilitySupport;
import io.github.malonetalk.service.SemanticVisibilitySupport.ColumnMergeSnapshot;
import io.github.malonetalk.service.SemanticVisibilitySupport.TableMergeSnapshot;
import io.github.malonetalk.service.SemanticVisibilitySupport.VisibilityContext;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogicalTableRelationServiceImpl implements LogicalTableRelationService {

    private static final Logger logger =
            LoggerFactory.getLogger(LogicalTableRelationServiceImpl.class);

    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final DatasourceService datasourceService;
    private final SemanticVisibilitySupport semanticVisibilitySupport;
    private final LogicalTableRelationSupport logicalTableRelationSupport;

    public LogicalTableRelationServiceImpl(
            LogicalTableRelationMapper logicalTableRelationMapper,
            DatasourceService datasourceService,
            SemanticVisibilitySupport semanticVisibilitySupport,
            LogicalTableRelationSupport logicalTableRelationSupport) {
        this.logicalTableRelationMapper = logicalTableRelationMapper;
        this.datasourceService = datasourceService;
        this.semanticVisibilitySupport = semanticVisibilitySupport;
        this.logicalTableRelationSupport = logicalTableRelationSupport;
    }

    @Override
    public List<LogicalTableRelationResponse> listByDatasourceIdAndTable(
            Integer datasourceId, String tableName) {
        Datasource datasource = requireDatasource(datasourceId);
        String normalizedTableName =
                logicalTableRelationSupport.normalizeTableName(tableName, "tableName");
        List<LogicalTableRelation> relations =
                logicalTableRelationMapper.selectByDatasourceIdAndSourceTable(
                        datasourceId, normalizedTableName);
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        return relations.stream()
                .map(relation -> mapResponse(visibilityContext, relation))
                .toList();
    }

    @Override
    public PageResponse<LogicalTableRelationResponse> listByDatasourceIdAndTable(
            Integer datasourceId, String tableName, PageRequest pageRequest) {
        Datasource datasource = requireDatasource(datasourceId);
        String normalizedTableName =
                logicalTableRelationSupport.normalizeTableName(tableName, "tableName");
        long total =
                logicalTableRelationMapper.countByDatasourceIdAndSourceTable(
                        datasourceId, normalizedTableName);
        if (total == 0) {
            return PageResponse.empty(pageRequest);
        }
        List<LogicalTableRelation> relations =
                logicalTableRelationMapper.selectPageByDatasourceIdAndSourceTable(
                        datasourceId,
                        normalizedTableName,
                        pageRequest.offset(),
                        pageRequest.pageSize());
        if (relations.isEmpty()) {
            return PageResponse.of(Collections.emptyList(), total, pageRequest);
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        List<LogicalTableRelationResponse> responses =
                relations.stream()
                        .map(relation -> mapResponse(visibilityContext, relation))
                        .toList();
        return PageResponse.of(responses, total, pageRequest);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse create(
            Integer datasourceId, String tableName, BindLogicalTableRelationRequest request) {
        Datasource datasource = requireDatasource(datasourceId);
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        RelationDraft draft =
                buildDraft(
                        visibilityContext,
                        tableName,
                        request.sourceColumnNames(),
                        request.targetTableName(),
                        request.targetColumnNames(),
                        request.description(),
                        request.enabled());

        LogicalTableRelation existing =
                logicalTableRelationMapper.selectByUniqueSourceKey(
                        datasourceId, draft.sourceTableName(), draft.sourceColumnSignature());
        if (existing != null) {
            throw new IllegalArgumentException(
                    "A logical relation already exists for the same source columns.");
        }

        LogicalTableRelation relation = new LogicalTableRelation();
        relation.setDatasourceId(datasourceId);
        relation.setSourceTableName(draft.sourceTableName());
        relation.setSourceColumnNamesJson(draft.sourceColumnNamesJson());
        relation.setSourceColumnSignature(draft.sourceColumnSignature());
        relation.setTargetTableName(draft.targetTableName());
        relation.setTargetColumnNamesJson(draft.targetColumnNamesJson());
        relation.setTargetColumnSignature(draft.targetColumnSignature());
        relation.setRelationType(LogicalTableRelationSupport.RELATION_TYPE_FOREIGN_KEY);
        relation.setDescription(draft.description());
        relation.setIsEnabled(draft.enabled());
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        logicalTableRelationMapper.insert(relation);
        return mapResponse(visibilityContext, relation);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse update(
            Integer datasourceId,
            String tableName,
            Integer relationId,
            UpdateLogicalTableRelationRequest request) {
        Datasource datasource = requireDatasource(datasourceId);
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        String normalizedTableName =
                logicalTableRelationSupport.normalizeTableName(tableName, "tableName");
        LogicalTableRelation existing =
                requireRelation(datasourceId, normalizedTableName, relationId);
        RelationDraft draft =
                buildDraft(
                        visibilityContext,
                        tableName,
                        request.sourceColumnNames(),
                        request.targetTableName(),
                        request.targetColumnNames(),
                        request.description(),
                        request.enabled());

        LogicalTableRelation duplicated =
                logicalTableRelationMapper.selectByUniqueSourceKey(
                        datasourceId, draft.sourceTableName(), draft.sourceColumnSignature());
        if (duplicated != null && !duplicated.getId().equals(existing.getId())) {
            throw new IllegalArgumentException(
                    "A logical relation already exists for the same source columns.");
        }

        existing.setSourceTableName(draft.sourceTableName());
        existing.setSourceColumnNamesJson(draft.sourceColumnNamesJson());
        existing.setSourceColumnSignature(draft.sourceColumnSignature());
        existing.setTargetTableName(draft.targetTableName());
        existing.setTargetColumnNamesJson(draft.targetColumnNamesJson());
        existing.setTargetColumnSignature(draft.targetColumnSignature());
        existing.setRelationType(LogicalTableRelationSupport.RELATION_TYPE_FOREIGN_KEY);
        existing.setDescription(draft.description());
        existing.setIsEnabled(draft.enabled());
        existing.setUpdateTime(LocalDateTime.now());
        logicalTableRelationMapper.update(existing);
        return mapResponse(visibilityContext, existing);
    }

    @Override
    @Transactional
    public boolean updateEnabled(
            Integer datasourceId, String tableName, Integer relationId, Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled cannot be null.");
        }
        String normalizedTableName =
                logicalTableRelationSupport.normalizeTableName(tableName, "tableName");
        requireDatasource(datasourceId);
        requireRelation(datasourceId, normalizedTableName, relationId);
        return logicalTableRelationMapper.updateEnabled(
                        relationId, datasourceId, normalizedTableName, enabled, LocalDateTime.now())
                > 0;
    }

    @Override
    @Transactional
    public boolean delete(Integer datasourceId, String tableName, Integer relationId) {
        String normalizedTableName =
                logicalTableRelationSupport.normalizeTableName(tableName, "tableName");
        requireDatasource(datasourceId);
        requireRelation(datasourceId, normalizedTableName, relationId);
        return logicalTableRelationMapper.deleteById(relationId, datasourceId, normalizedTableName)
                > 0;
    }

    @Override
    @Transactional
    public int deleteBatch(Integer datasourceId, String tableName, List<Integer> relationIds) {
        String normalizedTableName =
                logicalTableRelationSupport.normalizeTableName(tableName, "tableName");
        requireDatasource(datasourceId);
        List<Integer> normalizedRelationIds = normalizeRelationIds(relationIds, "relationIds");
        if (normalizedRelationIds.isEmpty()) {
            return 0;
        }
        return logicalTableRelationMapper.deleteByIdsAndSourceTable(
                datasourceId, normalizedTableName, normalizedRelationIds);
    }

    @Override
    public PageResponse<RelationCandidateTableResponse> listCandidateTables(
            Integer datasourceId, PageRequest pageRequest) {
        Datasource datasource = requireDatasource(datasourceId);
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        return paginateMapped(
                visibilityContext.mergeTables(),
                pageRequest,
                snapshot ->
                        semanticVisibilitySupport.isTableVisible(
                                snapshot.semanticTable(), snapshot.physicalTable()),
                snapshot ->
                        new RelationCandidateTableResponse(
                                semanticVisibilitySupport.resolveTableName(snapshot),
                                resolveTableDomain(snapshot),
                                resolveTableDescription(snapshot)));
    }

    @Override
    public PageResponse<RelationCandidateColumnResponse> listCandidateColumns(
            Integer datasourceId, String tableName, PageRequest pageRequest) {
        Datasource datasource = requireDatasource(datasourceId);
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        String normalizedTableName =
                logicalTableRelationSupport.normalizeTableName(tableName, "tableName");
        TableMergeSnapshot tableSnapshot =
                requireVisibleTable(visibilityContext, normalizedTableName, "candidate table");
        String canonicalTableName = semanticVisibilitySupport.resolveTableName(tableSnapshot);
        return paginateMapped(
                visibilityContext.mergeColumns(canonicalTableName),
                pageRequest,
                snapshot ->
                        semanticVisibilitySupport.isColumnVisible(
                                snapshot.semanticColumn(), snapshot.physicalColumn()),
                snapshot ->
                        new RelationCandidateColumnResponse(
                                snapshot.physicalColumn().getColumnName(),
                                resolveColumnDescription(snapshot),
                                snapshot.physicalColumn().getTypeName(),
                                snapshot.physicalColumn().isPrimaryKey()));
    }

    @Override
    public List<LogicalTableRelation> listEnabledByDatasourceIdAndSourceTable(
            Integer datasourceId, String tableName) {
        requireDatasource(datasourceId);
        String normalizedTableName =
                logicalTableRelationSupport.normalizeTableName(tableName, "tableName");
        return logicalTableRelationMapper.selectEnabledByDatasourceIdAndSourceTable(
                datasourceId, normalizedTableName);
    }

    @Override
    @Transactional
    public int deleteByDatasourceId(Integer datasourceId) {
        return logicalTableRelationMapper.deleteByDatasourceId(datasourceId);
    }

    private List<Integer> normalizeRelationIds(List<Integer> relationIds, String fieldName) {
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

    private Datasource requireDatasource(Integer datasourceId) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
        return datasource;
    }

    private LogicalTableRelation requireRelation(
            Integer datasourceId, String tableName, Integer relationId) {
        LogicalTableRelation relation = logicalTableRelationMapper.selectById(relationId);
        if (relation == null
                || !datasourceId.equals(relation.getDatasourceId())
                || !logicalTableRelationSupport.sameTableName(
                        relation.getSourceTableName(), tableName)) {
            throw new IllegalArgumentException("Logical relation does not exist.");
        }
        return relation;
    }

    private RelationDraft buildDraft(
            VisibilityContext visibilityContext,
            String tableName,
            List<String> sourceColumnNames,
            String targetTableName,
            List<String> targetColumnNames,
            String description,
            Boolean enabled) {
        String normalizedSourceTableName =
                logicalTableRelationSupport.normalizeTableName(tableName, "tableName");
        List<String> normalizedSourceColumns =
                logicalTableRelationSupport.normalizeColumnNames(
                        sourceColumnNames, "sourceColumnNames");
        String normalizedTargetTableName =
                logicalTableRelationSupport.normalizeTableName(targetTableName, "targetTableName");
        List<String> normalizedTargetColumns =
                logicalTableRelationSupport.normalizeColumnNames(
                        targetColumnNames, "targetColumnNames");
        if (normalizedSourceColumns.size() != normalizedTargetColumns.size()) {
            throw new IllegalArgumentException(
                    "Source columns and target columns must have the same size.");
        }

        TableMergeSnapshot sourceTableSnapshot =
                requireVisibleTable(visibilityContext, normalizedSourceTableName, "source table");
        TableMergeSnapshot targetTableSnapshot =
                requireVisibleTable(visibilityContext, normalizedTargetTableName, "target table");
        String canonicalSourceTableName =
                semanticVisibilitySupport.resolveTableName(sourceTableSnapshot);
        String canonicalTargetTableName =
                semanticVisibilitySupport.resolveTableName(targetTableSnapshot);
        requireVisibleColumns(
                visibilityContext, canonicalSourceTableName, normalizedSourceColumns, "source");
        requireVisibleColumns(
                visibilityContext, canonicalTargetTableName, normalizedTargetColumns, "target");

        return new RelationDraft(
                canonicalSourceTableName,
                logicalTableRelationSupport.toJson(normalizedSourceColumns),
                logicalTableRelationSupport.buildColumnSignature(normalizedSourceColumns),
                canonicalTargetTableName,
                logicalTableRelationSupport.toJson(normalizedTargetColumns),
                logicalTableRelationSupport.buildColumnSignature(normalizedTargetColumns),
                logicalTableRelationSupport.normalizeDescription(description),
                enabled);
    }

    private TableMergeSnapshot requireVisibleTable(
            VisibilityContext visibilityContext, String tableName, String roleLabel) {
        TableMergeSnapshot snapshot = visibilityContext.findMergedTable(tableName);
        if (snapshot == null) {
            throw new IllegalArgumentException(roleLabel + " does not exist: " + tableName);
        }
        if (!semanticVisibilitySupport.isTableVisible(
                snapshot.semanticTable(), snapshot.physicalTable())) {
            throw new IllegalArgumentException(roleLabel + " is not visible: " + tableName);
        }
        return snapshot;
    }

    private void requireVisibleColumns(
            VisibilityContext visibilityContext,
            String tableName,
            List<String> columnNames,
            String roleLabel) {
        for (String columnName : columnNames) {
            ColumnMergeSnapshot snapshot =
                    visibilityContext.findMergedColumn(tableName, columnName);
            if (snapshot == null || snapshot.physicalColumn() == null) {
                throw new IllegalArgumentException(
                        roleLabel + " column does not exist: " + tableName + "." + columnName);
            }
            if (!semanticVisibilitySupport.isColumnVisible(
                    snapshot.semanticColumn(), snapshot.physicalColumn())) {
                throw new IllegalArgumentException(
                        roleLabel + " column is not visible: " + tableName + "." + columnName);
            }
        }
    }

    private <S, T> PageResponse<T> paginateMapped(
            List<S> sourceItems,
            PageRequest pageRequest,
            Predicate<S> filter,
            Function<S, T> mapper) {
        if (sourceItems == null || sourceItems.isEmpty()) {
            return PageResponse.empty(pageRequest);
        }
        long start = pageRequest.offset();
        long endExclusive = start + pageRequest.pageSize();
        long matchedCount = 0L;
        List<T> pageItems = new java.util.ArrayList<>();
        for (S sourceItem : sourceItems) {
            if (!filter.test(sourceItem)) {
                continue;
            }
            if (matchedCount >= start && matchedCount < endExclusive) {
                pageItems.add(mapper.apply(sourceItem));
            }
            matchedCount++;
        }
        if (matchedCount == 0L) {
            return PageResponse.empty(pageRequest);
        }
        return PageResponse.of(pageItems, matchedCount, pageRequest);
    }

    private LogicalTableRelationResponse mapResponse(
            VisibilityContext visibilityContext, LogicalTableRelation relation) {
        ColumnDecodeResult sourceColumnResult =
                decodeColumnNames(
                        relation.getId(), relation.getSourceColumnNamesJson(), "sourceColumnNames");
        ColumnDecodeResult targetColumnResult =
                decodeColumnNames(
                        relation.getId(), relation.getTargetColumnNamesJson(), "targetColumnNames");
        RelationState relationState =
                evaluateRelationState(
                        visibilityContext,
                        relation.getSourceTableName(),
                        sourceColumnResult.columnNames(),
                        relation.getTargetTableName(),
                        targetColumnResult.columnNames(),
                        Boolean.TRUE.equals(relation.getIsEnabled()),
                        sourceColumnResult.errorMessage(),
                        targetColumnResult.errorMessage());
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

    private RelationState evaluateRelationState(
            VisibilityContext visibilityContext,
            String sourceTableName,
            List<String> sourceColumnNames,
            String targetTableName,
            List<String> targetColumnNames,
            boolean enabled,
            String sourceColumnErrorMessage,
            String targetColumnErrorMessage) {
        if (!enabled) {
            return new RelationState(false, "Relation is disabled.");
        }
        if (sourceColumnErrorMessage != null) {
            return new RelationState(false, sourceColumnErrorMessage);
        }
        if (targetColumnErrorMessage != null) {
            return new RelationState(false, targetColumnErrorMessage);
        }
        String sourceTableError =
                validateVisibleTable(visibilityContext, sourceTableName, "Source");
        if (sourceTableError != null) {
            return new RelationState(false, sourceTableError);
        }
        String targetTableError =
                validateVisibleTable(visibilityContext, targetTableName, "Target");
        if (targetTableError != null) {
            return new RelationState(false, targetTableError);
        }
        String sourceColumnError =
                validateVisibleColumns(
                        visibilityContext, sourceTableName, sourceColumnNames, "Source");
        if (sourceColumnError != null) {
            return new RelationState(false, sourceColumnError);
        }
        String targetColumnError =
                validateVisibleColumns(
                        visibilityContext, targetTableName, targetColumnNames, "Target");
        if (targetColumnError != null) {
            return new RelationState(false, targetColumnError);
        }
        return new RelationState(true, null);
    }

    private String validateVisibleTable(
            VisibilityContext visibilityContext, String tableName, String roleLabel) {
        TableMergeSnapshot snapshot = visibilityContext.findMergedTable(tableName);
        if (snapshot == null) {
            return roleLabel + " table does not exist: " + tableName;
        }
        if (!semanticVisibilitySupport.isTableVisible(
                snapshot.semanticTable(), snapshot.physicalTable())) {
            return roleLabel + " table is not visible: " + tableName;
        }
        return null;
    }

    private String validateVisibleColumns(
            VisibilityContext visibilityContext,
            String tableName,
            List<String> columnNames,
            String roleLabel) {
        for (String columnName : columnNames) {
            ColumnMergeSnapshot snapshot =
                    visibilityContext.findMergedColumn(tableName, columnName);
            if (snapshot == null || snapshot.physicalColumn() == null) {
                return roleLabel + " column does not exist: " + tableName + "." + columnName;
            }
            if (!semanticVisibilitySupport.isColumnVisible(
                    snapshot.semanticColumn(), snapshot.physicalColumn())) {
                return roleLabel + " column is not visible: " + tableName + "." + columnName;
            }
        }
        return null;
    }

    private String resolveTableDomain(TableMergeSnapshot snapshot) {
        if (snapshot.semanticTable() != null
                && snapshot.semanticTable().getDomain() != null
                && !snapshot.semanticTable().getDomain().isBlank()) {
            return snapshot.semanticTable().getDomain().trim();
        }
        if (snapshot.physicalTable() != null
                && snapshot.physicalTable().getDomain() != null
                && !snapshot.physicalTable().getDomain().isBlank()) {
            return snapshot.physicalTable().getDomain().trim();
        }
        return null;
    }

    private String resolveTableDescription(TableMergeSnapshot snapshot) {
        if (snapshot.semanticTable() != null
                && snapshot.semanticTable().getTableDescription() != null
                && !snapshot.semanticTable().getTableDescription().isBlank()) {
            return snapshot.semanticTable().getTableDescription().trim();
        }
        if (snapshot.physicalTable() != null
                && snapshot.physicalTable().getTableDescription() != null
                && !snapshot.physicalTable().getTableDescription().isBlank()) {
            return snapshot.physicalTable().getTableDescription().trim();
        }
        return "";
    }

    private String resolveColumnDescription(ColumnMergeSnapshot snapshot) {
        if (snapshot.semanticColumn() != null
                && snapshot.semanticColumn().getColumnDescription() != null
                && !snapshot.semanticColumn().getColumnDescription().isBlank()) {
            return snapshot.semanticColumn().getColumnDescription().trim();
        }
        ColumnInfo physicalColumn = snapshot.physicalColumn();
        if (physicalColumn.getRemarks() != null && !physicalColumn.getRemarks().isBlank()) {
            return physicalColumn.getRemarks().trim();
        }
        return "";
    }

    private ColumnDecodeResult decodeColumnNames(
            Integer relationId, String columnNamesJson, String fieldName) {
        try {
            return new ColumnDecodeResult(
                    logicalTableRelationSupport.fromJson(columnNamesJson, fieldName), null);
        } catch (IllegalArgumentException e) {
            String errorMessage =
                    "Invalid "
                            + fieldName
                            + " payload for logical relation "
                            + relationId
                            + ": "
                            + e.getMessage();
            logger.warn(errorMessage);
            return new ColumnDecodeResult(Collections.emptyList(), errorMessage);
        }
    }

    private record RelationDraft(
            String sourceTableName,
            String sourceColumnNamesJson,
            String sourceColumnSignature,
            String targetTableName,
            String targetColumnNamesJson,
            String targetColumnSignature,
            String description,
            Boolean enabled) {}

    private record ColumnDecodeResult(List<String> columnNames, String errorMessage) {}

    private record RelationState(boolean effective, String invalidReason) {}
}

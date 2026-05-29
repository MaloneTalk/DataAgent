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
package io.github.malonetalk.service.impl.semantic.relation;

import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.BindLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateColumnResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateTableResponse;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationRequest;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.ResolvedColumn;
import io.github.malonetalk.entity.ResolvedRelation;
import io.github.malonetalk.entity.ResolvedTable;
import io.github.malonetalk.service.semantic.SemanticContext;
import io.github.malonetalk.service.semantic.SemanticContextFactory;
import io.github.malonetalk.service.semantic.SemanticDatasourceService;
import io.github.malonetalk.service.semantic.SemanticPageService;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import io.github.malonetalk.service.semantic.relation.RelationSemanticPolicyService;
import io.github.malonetalk.service.semantic.relation.RelationSemanticPolicyService.RelationDraft;
import io.github.malonetalk.service.semantic.relation.RelationSemanticRepository;
import io.github.malonetalk.service.semantic.relation.RelationSemanticService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelationSemanticServiceImpl implements RelationSemanticService {

    private final RelationSemanticRepository relationSemanticRepository;
    private final LogicalTableRelationHelper logicalTableRelationHelper;
    private final SemanticContextFactory semanticContextFactory;
    private final SemanticPageService semanticPageService;
    private final RelationSemanticPolicyService relationSemanticPolicyService;
    private final SemanticDatasourceService semanticDatasourceService;

    public RelationSemanticServiceImpl(
            RelationSemanticRepository relationSemanticRepository,
            LogicalTableRelationHelper logicalTableRelationHelper,
            SemanticContextFactory semanticContextFactory,
            SemanticPageService semanticPageService,
            RelationSemanticPolicyService relationSemanticPolicyService,
            SemanticDatasourceService semanticDatasourceService) {
        this.relationSemanticRepository = relationSemanticRepository;
        this.logicalTableRelationHelper = logicalTableRelationHelper;
        this.semanticContextFactory = semanticContextFactory;
        this.semanticPageService = semanticPageService;
        this.relationSemanticPolicyService = relationSemanticPolicyService;
        this.semanticDatasourceService = semanticDatasourceService;
    }

    @Override
    public PageResponse<LogicalTableRelationResponse> getRelationPage(
            Integer datasourceId,
            String tableName,
            PageRequest pageRequest,
            String keywordPrefix,
            Boolean enabled,
            String sortOrder) {
        semanticPageService.validateSortOrder(sortOrder);
        Datasource datasource = semanticDatasourceService.findDatasourceOrNull(datasourceId);
        if (datasource == null) {
            return PageResponse.empty(pageRequest);
        }
        SemanticContext readContext = semanticContextFactory.createContext(datasource);
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        ResolvedTable table =
                relationSemanticPolicyService.requireVisibleTable(
                        readContext, normalizedTableName, "source table");
        String normalizedKeywordPrefix =
                relationSemanticPolicyService.normalizeKeywordPrefix(keywordPrefix);
        List<LogicalTableRelationResponse> relations =
                buildRelationManagementResponses(
                        readContext,
                        datasourceId,
                        table.canonicalName(),
                        normalizedKeywordPrefix,
                        enabled,
                        sortOrder);
        if (relations.isEmpty()) {
            return PageResponse.empty(pageRequest);
        }
        return semanticPageService.paginateMapped(
                relations, pageRequest, relation -> true, relation -> relation);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse createRelationSemantic(
            Integer datasourceId, String tableName, BindLogicalTableRelationRequest request) {
        Datasource datasource = loadDatasource(datasourceId);
        SemanticContext readContext = semanticContextFactory.createContext(datasource);
        RelationDraft draft = buildDraft(readContext, tableName, request);
        ensureNoDuplicateRelation(datasourceId, draft, null);
        ensureNoPhysicalDuplicateRelation(readContext, draft);
        LogicalTableRelation relation = buildNewRelation(datasourceId, draft);
        semanticDatasourceService.ensureWriteSuccess(
                relationSemanticRepository.save(relation),
                "Failed to create logical relation for source table "
                        + draft.sourceTableName()
                        + ".");
        return relationSemanticPolicyService.mapResponse(readContext, relation);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse updateRelationSemantic(
            Integer datasourceId,
            String tableName,
            Integer relationId,
            UpdateLogicalTableRelationRequest request) {
        Datasource datasource = loadDatasource(datasourceId);
        SemanticContext readContext = semanticContextFactory.createContext(datasource);
        LogicalTableRelation existing = loadTargetRelation(datasourceId, tableName, relationId);
        RelationDraft draft = buildDraft(readContext, tableName, request);
        ensureNoDuplicateRelation(datasourceId, draft, existing.getId());
        ensureNoPhysicalDuplicateRelation(readContext, draft);
        applyUpdate(existing, draft);
        semanticDatasourceService.ensureWriteSuccess(
                relationSemanticRepository.update(existing),
                "Failed to update logical relation " + relationId + ".");
        return relationSemanticPolicyService.mapResponse(readContext, existing);
    }

    @Override
    @Transactional
    public boolean updateRelationSemanticEnabled(
            Integer datasourceId, String tableName, Integer relationId, Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled cannot be null.");
        }
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        semanticDatasourceService.requireDatasource(datasourceId);
        LogicalTableRelation relation =
                requireRelation(datasourceId, normalizedTableName, relationId);
        if (Boolean.valueOf(enabled).equals(relation.getIsEnabled())) {
            return true;
        }
        semanticDatasourceService.ensureWriteSuccess(
                relationSemanticRepository.updateEnabled(
                        relationId,
                        datasourceId,
                        normalizedTableName,
                        enabled,
                        LocalDateTime.now()),
                "Failed to update logical relation enabled state for relation " + relationId + ".");
        return true;
    }

    @Override
    @Transactional
    public boolean deleteRelationSemantic(
            Integer datasourceId, String tableName, Integer relationId) {
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        semanticDatasourceService.requireDatasource(datasourceId);
        requireRelation(datasourceId, normalizedTableName, relationId);
        semanticDatasourceService.ensureWriteSuccess(
                relationSemanticRepository.deleteById(
                        relationId, datasourceId, normalizedTableName),
                "Failed to delete logical relation " + relationId + ".");
        return true;
    }

    @Override
    @Transactional
    public int deleteRelationSemantics(
            Integer datasourceId, String tableName, List<Integer> relationIds) {
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        semanticDatasourceService.requireDatasource(datasourceId);
        List<Integer> normalizedRelationIds =
                relationSemanticPolicyService.normalizeRelationIds(relationIds, "relationIds");
        if (normalizedRelationIds.isEmpty()) {
            return 0;
        }
        List<Integer> matchedRelationIds =
                relationSemanticRepository
                        .listByDatasourceIdAndSourceTable(datasourceId, normalizedTableName)
                        .stream()
                        .map(LogicalTableRelation::getId)
                        .filter(normalizedRelationIds::contains)
                        .distinct()
                        .toList();
        if (matchedRelationIds.size() != normalizedRelationIds.size()) {
            throw new IllegalArgumentException(
                    "Some logical relations do not exist or do not belong to source table "
                            + normalizedTableName
                            + ".");
        }
        int deletedCount =
                relationSemanticRepository.deleteByIdsAndSourceTable(
                        datasourceId, normalizedTableName, normalizedRelationIds);
        semanticDatasourceService.ensureWriteSuccess(
                deletedCount == normalizedRelationIds.size(),
                "Failed to delete all requested logical relations for source table "
                        + normalizedTableName
                        + ".");
        return deletedCount;
    }

    @Override
    public PageResponse<RelationCandidateTableResponse> getRelationCandidateTablePage(
            Integer datasourceId, PageRequest pageRequest, String keywordPrefix, String sortOrder) {
        semanticPageService.validateSortOrder(sortOrder);
        Datasource datasource = semanticDatasourceService.findDatasourceOrNull(datasourceId);
        if (datasource == null) {
            return PageResponse.empty(pageRequest);
        }
        List<ResolvedTable> sortedTables =
                semanticContextFactory.createContext(datasource).listTables().stream()
                        .filter(
                                table ->
                                        semanticPageService.matchesKeywordPrefix(
                                                table.canonicalName(), keywordPrefix))
                        .sorted(semanticPageService.buildResolvedTableComparator(sortOrder))
                        .toList();
        if (sortedTables.isEmpty()) {
            return PageResponse.empty(pageRequest);
        }
        return semanticPageService.paginateMapped(
                sortedTables,
                pageRequest,
                table -> table.hasPhysicalTable() && table.visible(),
                table ->
                        new RelationCandidateTableResponse(
                                table.canonicalName(), table.domain(), text(table.description())));
    }

    @Override
    public PageResponse<RelationCandidateColumnResponse> getRelationCandidateColumnPage(
            Integer datasourceId,
            String tableName,
            PageRequest pageRequest,
            String keywordPrefix,
            String sortOrder) {
        semanticPageService.validateSortOrder(sortOrder);
        Datasource datasource = semanticDatasourceService.findDatasourceOrNull(datasourceId);
        if (datasource == null) {
            return PageResponse.empty(pageRequest);
        }
        SemanticContext readContext = semanticContextFactory.createContext(datasource);
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        ResolvedTable table =
                relationSemanticPolicyService.requireVisibleTable(
                        readContext, normalizedTableName, "candidate table");
        List<ResolvedColumn> sortedColumns =
                readContext.listColumns(table.canonicalName()).stream()
                        .filter(
                                column ->
                                        semanticPageService.matchesKeywordPrefix(
                                                column.columnName(), keywordPrefix))
                        .sorted(semanticPageService.buildResolvedColumnComparator(sortOrder))
                        .toList();
        if (sortedColumns.isEmpty()) {
            return PageResponse.empty(pageRequest);
        }
        return semanticPageService.paginateMapped(
                sortedColumns,
                pageRequest,
                column -> column.hasPhysicalColumn() && column.visible(),
                column ->
                        new RelationCandidateColumnResponse(
                                column.columnName(),
                                text(column.description()),
                                column.typeName(),
                                column.primaryKey()));
    }

    private Datasource loadDatasource(Integer datasourceId) {
        return semanticDatasourceService.requireDatasource(datasourceId);
    }

    private RelationDraft buildDraft(
            SemanticContext readContext,
            String tableName,
            BindLogicalTableRelationRequest request) {
        return relationSemanticPolicyService.buildDraft(
                readContext,
                tableName,
                request.sourceColumnNames(),
                request.targetTableName(),
                request.targetColumnNames(),
                request.description(),
                request.enabled());
    }

    private RelationDraft buildDraft(
            SemanticContext readContext,
            String tableName,
            UpdateLogicalTableRelationRequest request) {
        return relationSemanticPolicyService.buildDraft(
                readContext,
                tableName,
                request.sourceColumnNames(),
                request.targetTableName(),
                request.targetColumnNames(),
                request.description(),
                request.enabled());
    }

    private LogicalTableRelation loadTargetRelation(
            Integer datasourceId, String tableName, Integer relationId) {
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        return requireRelation(datasourceId, normalizedTableName, relationId);
    }

    private void ensureNoDuplicateRelation(
            Integer datasourceId, RelationDraft draft, Integer currentRelationId) {
        LogicalTableRelation duplicated =
                relationSemanticRepository.findByUniqueSourceKey(
                        datasourceId, draft.sourceTableName(), draft.sourceColumnSignature());
        if (duplicated == null) {
            return;
        }
        if (currentRelationId != null && duplicated.getId().equals(currentRelationId)) {
            return;
        }
        throw new IllegalArgumentException(
                "A logical relation already exists for the same source columns.");
    }

    private void ensureNoPhysicalDuplicateRelation(
            SemanticContext readContext, RelationDraft draft) {
        boolean duplicated =
                readContext.listVisibleRelations(draft.sourceTableName()).stream()
                        .filter(
                                relation ->
                                        LogicalTableRelationHelper.RELATION_SOURCE_PHYSICAL.equals(
                                                relation.source()))
                        .anyMatch(relation -> sameRelationEndpoints(relation, draft));
        if (duplicated) {
            throw new IllegalArgumentException(
                    "A physical foreign key already exists for the same source and target"
                            + " columns.");
        }
    }

    private LogicalTableRelation buildNewRelation(Integer datasourceId, RelationDraft draft) {
        LogicalTableRelation relation = new LogicalTableRelation();
        relation.setDatasourceId(datasourceId);
        applyCreateDraft(relation, draft);
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        return relation;
    }

    private boolean sameRelationEndpoints(ResolvedRelation relation, RelationDraft draft) {
        return logicalTableRelationHelper.sameTableName(
                        relation.sourceTableName(), draft.sourceTableName())
                && logicalTableRelationHelper.sameTableName(
                        relation.targetTableName(), draft.targetTableName())
                && logicalTableRelationHelper
                        .buildColumnSignature(relation.sourceColumnNames())
                        .equals(draft.sourceColumnSignature())
                && logicalTableRelationHelper
                        .buildColumnSignature(relation.targetColumnNames())
                        .equals(draft.targetColumnSignature());
    }

    private void applyUpdate(LogicalTableRelation relation, RelationDraft draft) {
        applyCreateDraft(relation, draft);
        relation.setUpdateTime(LocalDateTime.now());
    }

    private void applyCreateDraft(LogicalTableRelation relation, RelationDraft draft) {
        relation.setSourceTableName(draft.sourceTableName());
        relation.setSourceColumnNamesJson(draft.sourceColumnNamesJson());
        relation.setSourceColumnSignature(draft.sourceColumnSignature());
        relation.setTargetTableName(draft.targetTableName());
        relation.setTargetColumnNamesJson(draft.targetColumnNamesJson());
        relation.setTargetColumnSignature(draft.targetColumnSignature());
        relation.setRelationType(LogicalTableRelationHelper.RELATION_TYPE_FOREIGN_KEY);
        relation.setDescription(draft.description());
        relation.setIsEnabled(draft.enabled());
    }

    private LogicalTableRelation requireRelation(
            Integer datasourceId, String tableName, Integer relationId) {
        LogicalTableRelation relation = relationSemanticRepository.findById(relationId);
        if (relation == null
                || !datasourceId.equals(relation.getDatasourceId())
                || !logicalTableRelationHelper.sameTableName(
                        relation.getSourceTableName(), tableName)) {
            throw new IllegalArgumentException("Logical relation does not exist.");
        }
        return relation;
    }

    private List<LogicalTableRelationResponse> buildRelationManagementResponses(
            SemanticContext readContext,
            Integer datasourceId,
            String canonicalTableName,
            String keywordPrefix,
            Boolean enabled,
            String sortOrder) {
        List<LogicalTableRelationResponse> responses = new ArrayList<>();
        readContext.listVisiblePhysicalRelations(canonicalTableName).stream()
                .filter(
                        relation ->
                                semanticPageService.matchesKeywordPrefix(
                                        relation.targetTableName(), keywordPrefix))
                .filter(relation -> enabled == null || enabled)
                .map(
                        relation ->
                                relationSemanticPolicyService.mapResolvedRelationResponse(
                                        readContext, datasourceId, relation))
                .forEach(responses::add);
        readContext.listLogicalRelations(canonicalTableName).stream()
                .filter(
                        relation ->
                                enabled == null
                                        || Boolean.valueOf(enabled).equals(relation.getIsEnabled()))
                .map(relation -> relationSemanticPolicyService.mapResponse(readContext, relation))
                .filter(
                        relation ->
                                semanticPageService.matchesKeywordPrefix(
                                        relation.targetTableName(), keywordPrefix))
                .forEach(responses::add);
        responses.sort(buildRelationResponseComparator(sortOrder));
        return List.copyOf(responses);
    }

    private Comparator<LogicalTableRelationResponse> buildRelationResponseComparator(
            String sortOrder) {
        Comparator<LogicalTableRelationResponse> comparator =
                Comparator.comparing(
                                (LogicalTableRelationResponse relation) ->
                                        LogicalTableRelationHelper.RELATION_SOURCE_PHYSICAL.equals(
                                                        relation.source())
                                                ? 0
                                                : 1)
                        .thenComparing(
                                relation -> relation.targetTableName().toLowerCase(Locale.ROOT))
                        .thenComparing(
                                relation ->
                                        logicalTableRelationHelper.buildColumnSignature(
                                                relation.sourceColumnNames()))
                        .thenComparing(
                                relation ->
                                        logicalTableRelationHelper.buildColumnSignature(
                                                relation.targetColumnNames()));
        return "desc".equalsIgnoreCase(sortOrder) ? comparator.reversed() : comparator;
    }

    private String text(String value) {
        return value != null ? value : "";
    }
}

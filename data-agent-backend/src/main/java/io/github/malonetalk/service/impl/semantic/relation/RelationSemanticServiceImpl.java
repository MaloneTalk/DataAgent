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
import java.util.Collections;
import java.util.List;
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
        Datasource datasource = semanticDatasourceService.requireDatasource(datasourceId);
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        String normalizedKeywordPrefix =
                relationSemanticPolicyService.normalizeKeywordPrefix(keywordPrefix);
        boolean sortDescending = "desc".equalsIgnoreCase(sortOrder);
        long total =
                relationSemanticRepository.countByDatasourceIdAndSourceTable(
                        datasourceId, normalizedTableName, normalizedKeywordPrefix, enabled);
        if (total == 0) {
            return PageResponse.empty(pageRequest);
        }
        List<LogicalTableRelation> relations =
                relationSemanticRepository.listPageByDatasourceIdAndSourceTable(
                        datasourceId,
                        normalizedTableName,
                        normalizedKeywordPrefix,
                        enabled,
                        sortDescending,
                        pageRequest.offset(),
                        pageRequest.pageSize());
        if (relations.isEmpty()) {
            return PageResponse.of(Collections.emptyList(), total, pageRequest);
        }
        SemanticContext readContext = semanticContextFactory.createContext(datasource);
        List<LogicalTableRelationResponse> responses =
                relations.stream()
                        .map(
                                relation ->
                                        relationSemanticPolicyService.mapResponse(
                                                readContext, relation))
                        .toList();
        return PageResponse.of(responses, total, pageRequest);
    }

    @Override
    @Transactional
    public LogicalTableRelationResponse createRelationSemantic(
            Integer datasourceId, String tableName, BindLogicalTableRelationRequest request) {
        Datasource datasource = loadDatasource(datasourceId);
        SemanticContext readContext = semanticContextFactory.createContext(datasource);
        RelationDraft draft = buildDraft(readContext, tableName, request);
        ensureNoDuplicateRelation(datasourceId, draft, null);
        LogicalTableRelation relation = buildNewRelation(datasourceId, draft);
        relationSemanticRepository.save(relation);
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
        applyUpdate(existing, draft);
        relationSemanticRepository.update(existing);
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
        requireRelation(datasourceId, normalizedTableName, relationId);
        return relationSemanticRepository.updateEnabled(
                relationId, datasourceId, normalizedTableName, enabled, LocalDateTime.now());
    }

    @Override
    @Transactional
    public boolean deleteRelationSemantic(
            Integer datasourceId, String tableName, Integer relationId) {
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        semanticDatasourceService.requireDatasource(datasourceId);
        requireRelation(datasourceId, normalizedTableName, relationId);
        return relationSemanticRepository.deleteById(relationId, datasourceId, normalizedTableName);
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
        return relationSemanticRepository.deleteByIdsAndSourceTable(
                datasourceId, normalizedTableName, normalizedRelationIds);
    }

    @Override
    public PageResponse<RelationCandidateTableResponse> getRelationCandidateTablePage(
            Integer datasourceId, PageRequest pageRequest, String keywordPrefix, String sortOrder) {
        semanticPageService.validateSortOrder(sortOrder);
        Datasource datasource = semanticDatasourceService.requireDatasource(datasourceId);
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
                ResolvedTable::visible,
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
        Datasource datasource = semanticDatasourceService.requireDatasource(datasourceId);
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
                ResolvedColumn::visible,
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

    private LogicalTableRelation buildNewRelation(Integer datasourceId, RelationDraft draft) {
        LogicalTableRelation relation = new LogicalTableRelation();
        relation.setDatasourceId(datasourceId);
        applyCreateDraft(relation, draft);
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        return relation;
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

    private String text(String value) {
        return value != null ? value : "";
    }
}

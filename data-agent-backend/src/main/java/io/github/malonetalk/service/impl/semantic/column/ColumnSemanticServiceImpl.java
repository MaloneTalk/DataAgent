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
package io.github.malonetalk.service.impl.semantic.column;

import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticUpdateRequest;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.ResolvedColumn;
import io.github.malonetalk.exception.SemanticSchemaException;
import io.github.malonetalk.service.semantic.SemanticContext;
import io.github.malonetalk.service.semantic.SemanticContextFactory;
import io.github.malonetalk.service.semantic.SemanticDatasourceService;
import io.github.malonetalk.service.semantic.SemanticManager.TableMergeSnapshot;
import io.github.malonetalk.service.semantic.SemanticManager.VisibilityContext;
import io.github.malonetalk.service.semantic.SemanticPageService;
import io.github.malonetalk.service.semantic.SemanticSnapshotFactory;
import io.github.malonetalk.service.semantic.column.ColumnSemanticRepository;
import io.github.malonetalk.service.semantic.column.ColumnSemanticService;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ColumnSemanticServiceImpl implements ColumnSemanticService {

    private final ColumnSemanticRepository columnSemanticRepository;
    private final LogicalTableRelationHelper logicalTableRelationHelper;
    private final SemanticContextFactory semanticContextFactory;
    private final SemanticPageService semanticPageService;
    private final SemanticDatasourceService semanticDatasourceService;
    private final SemanticSnapshotFactory semanticSnapshotFactory;

    public ColumnSemanticServiceImpl(
            ColumnSemanticRepository columnSemanticRepository,
            LogicalTableRelationHelper logicalTableRelationHelper,
            SemanticContextFactory semanticContextFactory,
            SemanticPageService semanticPageService,
            SemanticDatasourceService semanticDatasourceService,
            SemanticSnapshotFactory semanticSnapshotFactory) {
        this.columnSemanticRepository = columnSemanticRepository;
        this.logicalTableRelationHelper = logicalTableRelationHelper;
        this.semanticContextFactory = semanticContextFactory;
        this.semanticPageService = semanticPageService;
        this.semanticDatasourceService = semanticDatasourceService;
        this.semanticSnapshotFactory = semanticSnapshotFactory;
    }

    @Override
    public PageResponse<ColumnSemanticResponse> getColumnPage(
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
        VisibilityContext visibilityContext =
                semanticSnapshotFactory.createVisibilityContext(datasource);
        TableMergeSnapshot tableSnapshot =
                semanticSnapshotFactory.requirePhysicalTableSnapshotOrThrow(
                        visibilityContext, tableName);
        String canonicalTableName =
                semanticSnapshotFactory.resolveCanonicalTableName(tableSnapshot);

        SemanticContext context = semanticContextFactory.createContext(datasource);
        List<ResolvedColumn> sortedColumns =
                context.listColumns(canonicalTableName).stream()
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
                ResolvedColumn::hasPhysicalColumn,
                this::mapColumnResponse);
    }

    @Override
    public void updateColumnSemantic(
            Integer datasourceId, String tableName, ColumnSemanticUpdateRequest request) {
        Datasource datasource = loadUpdateDatasource(datasourceId);
        VisibilityContext visibilityContext =
                semanticSnapshotFactory.createVisibilityContext(datasource);
        String canonicalTableName = resolveCanonicalTableName(visibilityContext, tableName);
        String canonicalColumnName =
                resolveCanonicalColumnName(
                        visibilityContext, canonicalTableName, request.columnName());
        ColumnInfo existingOverlay =
                loadExistingOverlay(datasourceId, canonicalTableName, canonicalColumnName);
        if (existingOverlay != null) {
            applyUpdate(existingOverlay, canonicalTableName, canonicalColumnName, request);
            persistUpdatedOverlay(existingOverlay, canonicalTableName, canonicalColumnName);
            return;
        }
        persistNewOverlay(
                buildNewOverlay(request, datasourceId, canonicalTableName, canonicalColumnName),
                canonicalTableName,
                canonicalColumnName);
    }

    @Override
    public void resetColumnSemantic(Integer datasourceId, String tableName, String columnName) {
        Datasource datasource = loadResetDatasource(datasourceId);
        VisibilityContext visibilityContext =
                semanticSnapshotFactory.createVisibilityContext(datasource);
        String canonicalTableName = resolveCanonicalTableName(visibilityContext, tableName);
        String canonicalColumnName =
                resolveCanonicalColumnName(visibilityContext, canonicalTableName, columnName);
        List<Integer> matchedIds =
                findResetIds(datasourceId, canonicalTableName, canonicalColumnName);
        if (matchedIds.isEmpty()) {
            throw new SemanticSchemaException(
                    "No semantic metadata found for column "
                            + canonicalTableName
                            + "."
                            + canonicalColumnName
                            + ".");
        }
        semanticDatasourceService.ensureWriteSuccess(
                columnSemanticRepository.deleteByDatasourceIdAndIds(datasourceId, matchedIds) > 0,
                "Failed to reset column semantic metadata for column "
                        + canonicalTableName
                        + "."
                        + canonicalColumnName
                        + ".");
    }

    @Override
    public int resetColumnSemantics(
            Integer datasourceId, String tableName, List<String> columnNames) {
        semanticDatasourceService.requireDatasource(datasourceId);
        String normalizedTableName =
                logicalTableRelationHelper.normalizeTableName(tableName, "tableName");
        Set<String> normalizedColumnKeys =
                logicalTableRelationHelper.normalizeColumnNames(columnNames, "columnNames").stream()
                        .map(logicalTableRelationHelper::normalizeIdentifierKey)
                        .collect(
                                java.util.stream.Collectors.toCollection(
                                        java.util.LinkedHashSet::new));
        List<Integer> matchedIds =
                columnSemanticRepository.listByDatasourceId(datasourceId).stream()
                        .filter(
                                column ->
                                        logicalTableRelationHelper.sameTableName(
                                                column.getTableName(), normalizedTableName))
                        .filter(
                                column ->
                                        normalizedColumnKeys.contains(
                                                logicalTableRelationHelper.normalizeIdentifierKey(
                                                        column.getColumnName())))
                        .map(ColumnInfo::getId)
                        .distinct()
                        .toList();
        if (matchedIds.isEmpty()) {
            return 0;
        }
        return columnSemanticRepository.deleteByDatasourceIdAndIds(datasourceId, matchedIds);
    }

    private ColumnSemanticResponse mapColumnResponse(ResolvedColumn column) {
        return new ColumnSemanticResponse(
                column.semanticId(),
                column.columnName(),
                column.physicalDescription(),
                column.description(),
                column.typeName(),
                column.updateTime(),
                column.primaryKey());
    }

    private Datasource loadUpdateDatasource(Integer datasourceId) {
        return semanticDatasourceService.requireSemanticDatasource(
                datasourceId, "Cannot update column semantic because datasource does not exist: ");
    }

    private Datasource loadResetDatasource(Integer datasourceId) {
        return semanticDatasourceService.requireSemanticDatasource(
                datasourceId, "Cannot reset column semantic because datasource does not exist: ");
    }

    private String resolveCanonicalTableName(
            VisibilityContext visibilityContext, String tableName) {
        TableMergeSnapshot tableSnapshot =
                semanticSnapshotFactory.requirePhysicalTableSnapshotOrThrow(
                        visibilityContext, tableName);
        return semanticSnapshotFactory.resolveCanonicalTableName(tableSnapshot);
    }

    private String resolveCanonicalColumnName(
            VisibilityContext visibilityContext, String canonicalTableName, String columnName) {
        var columnSnapshot =
                semanticSnapshotFactory.requirePhysicalColumnSnapshotOrThrow(
                        visibilityContext, canonicalTableName, columnName);
        return columnSnapshot.physicalColumn().getColumnName();
    }

    private ColumnInfo loadExistingOverlay(
            Integer datasourceId, String canonicalTableName, String canonicalColumnName) {
        return columnSemanticRepository.findByDatasourceIdAndTableNameAndColumnName(
                datasourceId, canonicalTableName, canonicalColumnName);
    }

    private void applyUpdate(
            ColumnInfo existingOverlay,
            String canonicalTableName,
            String canonicalColumnName,
            ColumnSemanticUpdateRequest request) {
        existingOverlay.setTableName(canonicalTableName);
        existingOverlay.setColumnName(canonicalColumnName);
        if (request.columnDescription() != null) {
            existingOverlay.setColumnDescription(request.columnDescription());
        }
        existingOverlay.setIsVisible(request.isVisible());
    }

    private ColumnInfo buildNewOverlay(
            ColumnSemanticUpdateRequest request,
            Integer datasourceId,
            String canonicalTableName,
            String canonicalColumnName) {
        ColumnInfo semanticColumn = new ColumnInfo();
        semanticColumn.setDatasourceId(datasourceId);
        semanticColumn.setTableName(canonicalTableName);
        semanticColumn.setColumnName(canonicalColumnName);
        semanticColumn.setColumnDescription(request.columnDescription());
        semanticColumn.setIsActive(true);
        semanticColumn.setIsVisible(request.isVisible());
        return semanticColumn;
    }

    private void persistUpdatedOverlay(
            ColumnInfo existingOverlay, String canonicalTableName, String canonicalColumnName) {
        semanticDatasourceService.ensureWriteSuccess(
                columnSemanticRepository.update(existingOverlay),
                "Failed to update column semantic metadata for column "
                        + canonicalTableName
                        + "."
                        + canonicalColumnName
                        + ".");
    }

    private void persistNewOverlay(
            ColumnInfo semanticColumn, String canonicalTableName, String canonicalColumnName) {
        semanticDatasourceService.ensureWriteSuccess(
                columnSemanticRepository.save(semanticColumn),
                "Failed to save column semantic metadata for column "
                        + canonicalTableName
                        + "."
                        + canonicalColumnName
                        + ".");
    }

    private List<Integer> findResetIds(
            Integer datasourceId, String canonicalTableName, String canonicalColumnName) {
        return columnSemanticRepository.listByDatasourceId(datasourceId).stream()
                .filter(
                        semanticColumn ->
                                logicalTableRelationHelper.sameTableName(
                                        semanticColumn.getTableName(), canonicalTableName))
                .filter(
                        semanticColumn ->
                                logicalTableRelationHelper
                                        .normalizeIdentifierKey(semanticColumn.getColumnName())
                                        .equals(
                                                logicalTableRelationHelper.normalizeIdentifierKey(
                                                        canonicalColumnName)))
                .map(ColumnInfo::getId)
                .distinct()
                .toList();
    }
}

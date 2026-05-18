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
package io.github.malonetalk.service.impl.semantic.table;

import static io.github.malonetalk.common.SemanticConstants.MAX_RELATIONS_PER_TABLE;

import io.github.malonetalk.agent.tools.response.ColumnPromptResponse;
import io.github.malonetalk.agent.tools.response.TablePromptResponse;
import io.github.malonetalk.agent.tools.response.TableRelationResponse;
import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.TableSchemaSemanticPrompt;
import io.github.malonetalk.dto.semantic.TableSemanticResponse;
import io.github.malonetalk.dto.semantic.TableSemanticUpdateRequest;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.ResolvedColumn;
import io.github.malonetalk.entity.ResolvedRelation;
import io.github.malonetalk.entity.ResolvedTable;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.exception.SemanticSchemaException;
import io.github.malonetalk.service.ActiveDatasourceSupport;
import io.github.malonetalk.service.semantic.SemanticContext;
import io.github.malonetalk.service.semantic.SemanticContextFactory;
import io.github.malonetalk.service.semantic.SemanticDatasourceService;
import io.github.malonetalk.service.semantic.SemanticManager.TableMergeSnapshot;
import io.github.malonetalk.service.semantic.SemanticPageService;
import io.github.malonetalk.service.semantic.SemanticResolver;
import io.github.malonetalk.service.semantic.SemanticService;
import io.github.malonetalk.service.semantic.SemanticSnapshotFactory;
import io.github.malonetalk.service.semantic.table.TableSemanticRepository;
import io.github.malonetalk.service.semantic.table.TableSemanticService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TableSemanticServiceImpl implements TableSemanticService {

    private final ActiveDatasourceSupport activeDatasourceSupport;
    private final TableSemanticRepository tableSemanticRepository;
    private final SemanticContextFactory semanticContextFactory;
    private final SemanticResolver semanticResolver;
    private final SemanticPageService semanticPageService;
    private final SemanticService semanticService;
    private final SemanticDatasourceService semanticDatasourceService;
    private final SemanticSnapshotFactory semanticSnapshotFactory;

    public TableSemanticServiceImpl(
            ActiveDatasourceSupport activeDatasourceSupport,
            TableSemanticRepository tableSemanticRepository,
            SemanticContextFactory semanticContextFactory,
            SemanticResolver semanticResolver,
            SemanticPageService semanticPageService,
            SemanticService semanticService,
            SemanticDatasourceService semanticDatasourceService,
            SemanticSnapshotFactory semanticSnapshotFactory) {
        this.activeDatasourceSupport = activeDatasourceSupport;
        this.tableSemanticRepository = tableSemanticRepository;
        this.semanticContextFactory = semanticContextFactory;
        this.semanticResolver = semanticResolver;
        this.semanticPageService = semanticPageService;
        this.semanticService = semanticService;
        this.semanticDatasourceService = semanticDatasourceService;
        this.semanticSnapshotFactory = semanticSnapshotFactory;
    }

    @Override
    public PageResponse<TableSemanticResponse> getTablePage(
            Integer datasourceId, PageRequest pageRequest, String keywordPrefix, String sortOrder) {
        semanticPageService.validateSortOrder(sortOrder);
        Datasource datasource = getActiveDatasource();
        if (datasourceId != null) {
            datasource = semanticDatasourceService.findDatasourceOrNull(datasourceId);
        }
        if (datasource == null) {
            return PageResponse.empty(pageRequest);
        }

        SemanticContext context = semanticContextFactory.createContext(datasource);
        List<ResolvedTable> sortedTables =
                context.listTables().stream()
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
                sortedTables, pageRequest, ResolvedTable::hasPhysicalTable, this::mapTableResponse);
    }

    @Override
    public PageResponse<TablePromptResponse> getVisibleTablePromptPage(PageRequest pageRequest) {
        Datasource datasource = getActiveDatasource();
        if (datasource == null) {
            return PageResponse.empty(pageRequest);
        }
        SemanticContext context = semanticContextFactory.createContext(datasource);
        List<ResolvedTable> visibleTables =
                context.listTables().stream()
                        .filter(ResolvedTable::visible)
                        .sorted(semanticPageService.buildResolvedTableComparator("asc"))
                        .toList();
        if (visibleTables.isEmpty()) {
            return PageResponse.empty(pageRequest);
        }
        List<ResolvedTable> pageTables = semanticPageService.sliceItems(visibleTables, pageRequest);
        if (pageTables.isEmpty()) {
            return PageResponse.empty(pageRequest);
        }
        List<TablePromptResponse> pageItems =
                pageTables.stream().map(table -> mapVisibleTablePrompt(context, table)).toList();
        return PageResponse.of(pageItems, visibleTables.size(), pageRequest);
    }

    @Override
    public void updateTableSemantic(TableSemanticUpdateRequest request) {
        Datasource datasource = loadUpdateDatasource(request.datasourceId());
        String canonicalTableName = resolveCanonicalTableName(datasource, request.tableName());
        TableInfo existingOverlay = loadExistingOverlay(request.datasourceId(), canonicalTableName);
        if (existingOverlay != null) {
            applyUpdate(existingOverlay, canonicalTableName, request);
            persistUpdatedOverlay(existingOverlay, canonicalTableName);
            return;
        }
        persistNewOverlay(buildNewOverlay(request, canonicalTableName), canonicalTableName);
    }

    @Override
    public void resetTableSemantic(Integer datasourceId, String tableName) {
        Datasource datasource = loadResetDatasource(datasourceId);
        String canonicalTableName = resolveCanonicalTableName(datasource, tableName);
        List<Integer> matchedIds = findResetIds(datasourceId, canonicalTableName);
        if (matchedIds.isEmpty()) {
            throw new SemanticSchemaException(
                    "No semantic metadata found for table " + canonicalTableName + ".");
        }
        semanticDatasourceService.ensureWriteSuccess(
                tableSemanticRepository.deleteByDatasourceIdAndIds(datasourceId, matchedIds) > 0,
                "Failed to reset table semantic metadata for table " + canonicalTableName + ".");
    }

    @Override
    public int resetTableSemantics(Integer datasourceId, List<String> tableNames) {
        semanticDatasourceService.requireDatasource(datasourceId);
        Set<String> normalizedTableKeys =
                semanticService.normalizeIdentifierKeys(tableNames, "tableNames");
        List<Integer> matchedIds =
                tableSemanticRepository.listByDatasourceId(datasourceId).stream()
                        .filter(
                                table ->
                                        normalizedTableKeys.contains(
                                                semanticService.normalizeIdentifierKey(
                                                        table.getTableName())))
                        .map(TableInfo::getId)
                        .distinct()
                        .toList();
        if (matchedIds.isEmpty()) {
            return 0;
        }
        return tableSemanticRepository.deleteByDatasourceIdAndIds(datasourceId, matchedIds);
    }

    @Override
    public TableSchemaSemanticPrompt getTableSchema(
            String tableName, PageRequest columnPageRequest) {
        Datasource datasource = getActiveDatasource();
        if (datasource == null) {
            throw new SemanticSchemaException(
                    "No active datasource available, cannot get table schema.");
        }
        SemanticContext readContext = semanticContextFactory.createContext(datasource);
        ResolvedTable table = readContext.findTable(tableName);
        if (table == null || !table.hasPhysicalTable()) {
            throw new SemanticSchemaException("Table " + tableName + " does not exist.");
        }
        if (!table.visible()) {
            throw new SemanticSchemaException(
                    "Table " + tableName + " is not visible in semantic metadata.");
        }

        String resolvedTableName = table.canonicalName();
        List<ResolvedColumn> columns = readContext.listColumns(resolvedTableName);
        if (columns.isEmpty()) {
            throw new SemanticSchemaException(
                    "Table " + tableName + " does not exist or has no column information.");
        }

        return new TableSchemaSemanticPrompt(
                resolvedTableName,
                table.domain(),
                text(table.description()),
                semanticPageService.paginateMapped(
                        columns,
                        columnPageRequest,
                        ResolvedColumn::visible,
                        this::mapColumnPrompt));
    }

    private TableSemanticResponse mapTableResponse(ResolvedTable table) {
        return new TableSemanticResponse(
                table.semanticId(),
                table.canonicalName(),
                table.domain(),
                table.physicalDescription(),
                table.description(),
                table.updateTime());
    }

    private TablePromptResponse mapVisibleTablePrompt(
            SemanticContext context, ResolvedTable table) {
        List<TableRelationResponse> allRelations =
                context.listVisibleRelations(table.canonicalName()).stream()
                        .map(this::toRelationResponse)
                        .toList();
        boolean truncated = allRelations.size() > MAX_RELATIONS_PER_TABLE;
        List<TableRelationResponse> relations =
                truncated ? allRelations.subList(0, MAX_RELATIONS_PER_TABLE) : allRelations;
        return new TablePromptResponse(
                table.canonicalName(),
                table.domain(),
                text(table.description()),
                relations,
                truncated);
    }

    private ColumnPromptResponse mapColumnPrompt(ResolvedColumn column) {
        return new ColumnPromptResponse(
                column.columnName(),
                column.typeText(),
                column.primaryKey(),
                column.nullable(),
                column.defaultValue(),
                text(column.description()));
    }

    private TableRelationResponse toRelationResponse(ResolvedRelation relation) {
        return new TableRelationResponse(
                relation.relationType(),
                relation.source(),
                relation.sourceTableName(),
                relation.sourceColumnNames(),
                relation.targetTableName(),
                relation.targetColumnNames(),
                relation.description());
    }

    private Datasource getActiveDatasource() {
        return activeDatasourceSupport.getActiveDatasource();
    }

    private Datasource loadUpdateDatasource(Integer datasourceId) {
        return semanticDatasourceService.requireSemanticDatasource(
                datasourceId, "Cannot update table semantic because datasource does not exist: ");
    }

    private Datasource loadResetDatasource(Integer datasourceId) {
        return semanticDatasourceService.requireSemanticDatasource(
                datasourceId, "Cannot reset table semantic because datasource does not exist: ");
    }

    private String resolveCanonicalTableName(Datasource datasource, String tableName) {
        TableMergeSnapshot snapshot =
                semanticSnapshotFactory.requirePhysicalTableSnapshotOrThrow(datasource, tableName);
        return semanticSnapshotFactory.resolveCanonicalTableName(snapshot);
    }

    private TableInfo loadExistingOverlay(Integer datasourceId, String canonicalTableName) {
        return semanticService.findSemanticTable(
                tableSemanticRepository.listByDatasourceId(datasourceId),
                datasourceId,
                canonicalTableName);
    }

    private void applyUpdate(
            TableInfo existingOverlay,
            String canonicalTableName,
            TableSemanticUpdateRequest request) {
        existingOverlay.setTableName(canonicalTableName);
        if (request.tableDescription() != null) {
            existingOverlay.setTableDescription(request.tableDescription());
        }
        if (request.domain() != null) {
            existingOverlay.setDomain(request.domain());
        }
        existingOverlay.setIsVisible(request.isVisible());
    }

    private TableInfo buildNewOverlay(
            TableSemanticUpdateRequest request, String canonicalTableName) {
        TableInfo semanticTable = new TableInfo();
        semanticTable.setTableName(canonicalTableName);
        semanticTable.setDomain(request.domain());
        semanticTable.setTableDescription(request.tableDescription());
        semanticTable.setDatasourceId(request.datasourceId());
        semanticTable.setIsActive(true);
        semanticTable.setIsVisible(request.isVisible());
        return semanticTable;
    }

    private void persistUpdatedOverlay(TableInfo existingOverlay, String canonicalTableName) {
        semanticDatasourceService.ensureWriteSuccess(
                tableSemanticRepository.update(existingOverlay),
                "Failed to update table semantic metadata for table " + canonicalTableName + ".");
    }

    private void persistNewOverlay(TableInfo semanticTable, String canonicalTableName) {
        semanticDatasourceService.ensureWriteSuccess(
                tableSemanticRepository.save(semanticTable),
                "Failed to save table semantic metadata for table " + canonicalTableName + ".");
    }

    private List<Integer> findResetIds(Integer datasourceId, String canonicalTableName) {
        return tableSemanticRepository.listByDatasourceId(datasourceId).stream()
                .filter(
                        tableInfo ->
                                semanticService
                                        .normalizeIdentifierKey(tableInfo.getTableName())
                                        .equals(
                                                semanticService.normalizeIdentifierKey(
                                                        canonicalTableName)))
                .map(TableInfo::getId)
                .distinct()
                .toList();
    }

    private String text(String value) {
        return value != null ? value : "";
    }
}

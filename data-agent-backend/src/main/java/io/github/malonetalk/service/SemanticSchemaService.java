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
package io.github.malonetalk.service;

import io.github.malonetalk.agent.datasource.ColumnInfo;
import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.agent.datasource.TableRelationInfo;
import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticUpdateRequest;
import io.github.malonetalk.dto.semantic.TableSchemaSemanticPrompt;
import io.github.malonetalk.dto.semantic.TableSemanticResponse;
import io.github.malonetalk.dto.semantic.TableSemanticUpdateRequest;
import io.github.malonetalk.dto.toolresponse.ColumnSemanticPrompt;
import io.github.malonetalk.dto.toolresponse.TableRelationToolResponse;
import io.github.malonetalk.dto.toolresponse.TableSemanticPrompt;
import io.github.malonetalk.entity.ColumnSemanticInfo;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.service.SemanticVisibilitySupport.ColumnMergeSnapshot;
import io.github.malonetalk.service.SemanticVisibilitySupport.TableMergeSnapshot;
import io.github.malonetalk.service.SemanticVisibilitySupport.VisibilityContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SemanticSchemaService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticSchemaService.class);

    private final DatasourceService datasourceService;
    private final ActiveDatasourceSupport activeDatasourceSupport;
    private final TableInfoService tableInfoService;
    private final ColumnSemanticInfoService columnSemanticInfoService;
    private final LogicalTableRelationService logicalTableRelationService;
    private final LogicalTableRelationSupport logicalTableRelationSupport;
    private final SemanticVisibilitySupport semanticVisibilitySupport;
    private final SchemaReader schemaReader;

    public SemanticSchemaService(
            DatasourceService datasourceService,
            ActiveDatasourceSupport activeDatasourceSupport,
            TableInfoService tableInfoService,
            ColumnSemanticInfoService columnSemanticInfoService,
            LogicalTableRelationService logicalTableRelationService,
            LogicalTableRelationSupport logicalTableRelationSupport,
            SemanticVisibilitySupport semanticVisibilitySupport,
            SchemaReader schemaReader) {
        this.datasourceService = datasourceService;
        this.activeDatasourceSupport = activeDatasourceSupport;
        this.tableInfoService = tableInfoService;
        this.columnSemanticInfoService = columnSemanticInfoService;
        this.logicalTableRelationService = logicalTableRelationService;
        this.logicalTableRelationSupport = logicalTableRelationSupport;
        this.semanticVisibilitySupport = semanticVisibilitySupport;
        this.schemaReader = schemaReader;
    }

    public List<TableSemanticResponse> getAllTables() {
        Datasource datasource = getActiveDatasource();
        if (datasource == null) {
            return Collections.emptyList();
        }
        return mapTableResponses(semanticVisibilitySupport.mergeTables(datasource));
    }

    public PageResponse<TableSemanticResponse> getTablePage(
            Integer datasourceId, PageRequest pageRequest) {
        Datasource datasource = getActiveDatasource();
        if (datasourceId != null) {
            datasource = datasourceService.findById(datasourceId);
        }
        if (datasource == null) {
            return PageResponse.empty(pageRequest);
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        return paginateMapped(
                visibilityContext.mergeTables(),
                pageRequest,
                snapshot -> true,
                this::mapTableResponse);
    }

    public List<TableSemanticResponse> getAllTables(Integer datasourceId) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return Collections.emptyList();
        }
        return mapTableResponses(semanticVisibilitySupport.mergeTables(datasource));
    }

    public List<TableSemanticPrompt> getVisibleTablePrompts() {
        Datasource datasource = getActiveDatasource();
        if (datasource == null) {
            return Collections.emptyList();
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        return visibilityContext.mergeTables().stream()
                .filter(
                        snapshot ->
                                isTableVisible(snapshot.semanticTable(), snapshot.physicalTable()))
                .map(
                        snapshot ->
                                new TableSemanticPrompt(
                                        snapshot.semanticTable() != null
                                                ? snapshot.semanticTable().getTableName()
                                                : snapshot.physicalTable().getTableName(),
                                        resolveTableDomain(
                                                snapshot.physicalTable(), snapshot.semanticTable()),
                                        resolveTableSemantic(
                                                snapshot.physicalTable(),
                                                snapshot.semanticTable())))
                .toList();
    }

    public PageResponse<TableSemanticPrompt> getVisibleTablePromptPage(PageRequest pageRequest) {
        Datasource datasource = getActiveDatasource();
        if (datasource == null) {
            return PageResponse.empty(pageRequest);
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        return paginateMapped(
                visibilityContext.mergeTables(),
                pageRequest,
                snapshot -> isTableVisible(snapshot.semanticTable(), snapshot.physicalTable()),
                snapshot ->
                        new TableSemanticPrompt(
                                resolveCanonicalTableName(snapshot),
                                resolveTableDomain(
                                        snapshot.physicalTable(), snapshot.semanticTable()),
                                resolveTableSemantic(
                                        snapshot.physicalTable(), snapshot.semanticTable())));
    }

    public boolean updateTableSemantic(TableSemanticUpdateRequest request) {
        Datasource datasource = datasourceService.findById(request.datasourceId());
        if (datasource == null) {
            return false;
        }

        TableMergeSnapshot tableSnapshot =
                requirePhysicalTableSnapshot(datasource, request.tableName());
        if (tableSnapshot == null) {
            return false;
        }
        String canonicalTableName = resolveCanonicalTableName(tableSnapshot);

        TableInfo tableInfo =
                findPreferredSemanticTable(request.datasourceId(), canonicalTableName);
        if (tableInfo != null) {
            tableInfo.setTableName(canonicalTableName);
            if (request.tableDescription() != null) {
                tableInfo.setTableDescription(request.tableDescription());
            }
            if (request.domain() != null) {
                tableInfo.setDomain(request.domain());
            }
            tableInfo.setIsVisible(request.isVisible());
            return tableInfoService.update(tableInfo);
        }

        TableInfo semanticTable = new TableInfo();
        semanticTable.setTableName(canonicalTableName);
        semanticTable.setDomain(request.domain());
        semanticTable.setTableDescription(request.tableDescription());
        semanticTable.setDatasourceId(request.datasourceId());
        semanticTable.setIsActive(true);
        semanticTable.setIsVisible(request.isVisible());
        return tableInfoService.save(semanticTable);
    }

    public boolean resetTableSemantic(Integer datasourceId, String tableName) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return false;
        }
        TableMergeSnapshot tableSnapshot = requirePhysicalTableSnapshot(datasource, tableName);
        if (tableSnapshot == null) {
            return false;
        }
        List<Integer> matchedIds =
                findMatchingSemanticTables(datasourceId, resolveCanonicalTableName(tableSnapshot))
                        .stream()
                        .map(TableInfo::getId)
                        .distinct()
                        .toList();
        if (matchedIds.isEmpty()) {
            return false;
        }
        return tableInfoService.deleteByDatasourceIdAndIds(datasourceId, matchedIds) > 0;
    }

    public int resetTableSemantics(Integer datasourceId, List<String> tableNames) {
        requireDatasource(datasourceId);
        Set<String> normalizedTableKeys = normalizeIdentifierKeys(tableNames, "tableNames");
        List<Integer> matchedIds =
                tableInfoService.findByDatasourceId(datasourceId).stream()
                        .filter(
                                table ->
                                        normalizedTableKeys.contains(
                                                normalizeIdentifierKey(table.getTableName())))
                        .map(TableInfo::getId)
                        .distinct()
                        .toList();
        if (matchedIds.isEmpty()) {
            return 0;
        }
        return tableInfoService.deleteByDatasourceIdAndIds(datasourceId, matchedIds);
    }

    public List<ColumnSemanticResponse> getAllColumns(Integer datasourceId, String tableName) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return Collections.emptyList();
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        return mapColumnResponses(visibilityContext.mergeColumns(tableName));
    }

    public PageResponse<ColumnSemanticResponse> getColumnPage(
            Integer datasourceId, String tableName, PageRequest pageRequest) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return PageResponse.empty(pageRequest);
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        return paginateMapped(
                visibilityContext.mergeColumns(tableName),
                pageRequest,
                snapshot -> true,
                this::mapColumnResponse);
    }

    public boolean updateColumnSemantic(
            Integer datasourceId, String tableName, ColumnSemanticUpdateRequest request) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return false;
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        TableMergeSnapshot tableSnapshot =
                requirePhysicalTableSnapshot(visibilityContext, tableName);
        if (tableSnapshot == null) {
            return false;
        }
        String canonicalTableName = resolveCanonicalTableName(tableSnapshot);
        ColumnMergeSnapshot columnSnapshot =
                visibilityContext.findMergedColumn(canonicalTableName, request.columnName());
        if (columnSnapshot == null || columnSnapshot.physicalColumn() == null) {
            return false;
        }
        String canonicalColumnName = columnSnapshot.physicalColumn().getColumnName();

        ColumnSemanticInfo columnSemanticInfo =
                findPreferredSemanticColumn(datasourceId, canonicalTableName, canonicalColumnName);
        if (columnSemanticInfo != null) {
            columnSemanticInfo.setTableName(canonicalTableName);
            columnSemanticInfo.setColumnName(canonicalColumnName);
            if (request.columnDescription() != null) {
                columnSemanticInfo.setColumnDescription(request.columnDescription());
            }
            columnSemanticInfo.setIsVisible(request.isVisible());
            return columnSemanticInfoService.update(columnSemanticInfo);
        }

        ColumnSemanticInfo semanticColumn = new ColumnSemanticInfo();
        semanticColumn.setDatasourceId(datasourceId);
        semanticColumn.setTableName(canonicalTableName);
        semanticColumn.setColumnName(canonicalColumnName);
        semanticColumn.setColumnDescription(request.columnDescription());
        semanticColumn.setIsActive(true);
        semanticColumn.setIsVisible(request.isVisible());
        return columnSemanticInfoService.save(semanticColumn);
    }

    public boolean resetColumnSemantic(Integer datasourceId, String tableName, String columnName) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return false;
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        TableMergeSnapshot tableSnapshot =
                requirePhysicalTableSnapshot(visibilityContext, tableName);
        if (tableSnapshot == null) {
            return false;
        }
        String canonicalTableName = resolveCanonicalTableName(tableSnapshot);
        ColumnMergeSnapshot columnSnapshot =
                visibilityContext.findMergedColumn(canonicalTableName, columnName);
        if (columnSnapshot == null || columnSnapshot.physicalColumn() == null) {
            return false;
        }
        List<Integer> matchedIds =
                findMatchingSemanticColumns(
                                datasourceId,
                                canonicalTableName,
                                columnSnapshot.physicalColumn().getColumnName())
                        .stream()
                        .map(ColumnSemanticInfo::getId)
                        .distinct()
                        .toList();
        if (matchedIds.isEmpty()) {
            return false;
        }
        return columnSemanticInfoService.deleteByDatasourceIdAndIds(datasourceId, matchedIds) > 0;
    }

    public int resetColumnSemantics(
            Integer datasourceId, String tableName, List<String> columnNames) {
        requireDatasource(datasourceId);
        String normalizedTableName =
                logicalTableRelationSupport.normalizeTableName(tableName, "tableName");
        Set<String> normalizedColumnKeys = normalizeIdentifierKeys(columnNames, "columnNames");
        List<Integer> matchedIds =
                columnSemanticInfoService.findByDatasourceId(datasourceId).stream()
                        .filter(
                                column ->
                                        logicalTableRelationSupport.sameTableName(
                                                column.getTableName(), normalizedTableName))
                        .filter(
                                column ->
                                        normalizedColumnKeys.contains(
                                                normalizeIdentifierKey(column.getColumnName())))
                        .map(ColumnSemanticInfo::getId)
                        .distinct()
                        .toList();
        if (matchedIds.isEmpty()) {
            return 0;
        }
        return columnSemanticInfoService.deleteByDatasourceIdAndIds(datasourceId, matchedIds);
    }

    public List<ColumnSemanticPrompt> getVisibleColumnPrompts(
            Integer datasourceId, String tableName) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return Collections.emptyList();
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        return visibilityContext.mergeColumns(tableName).stream()
                .filter(
                        snapshot ->
                                isColumnVisible(
                                        snapshot.semanticColumn(), snapshot.physicalColumn()))
                .map(
                        snapshot ->
                                mapColumnPrompt(
                                        snapshot.physicalColumn(), snapshot.semanticColumn()))
                .toList();
    }

    public TableSchemaSemanticPrompt getTableSchema(String tableName) {
        return getTableSchema(
                tableName,
                new PageRequest(1, PageRequest.MAX_PAGE_SIZE),
                new PageRequest(1, PageRequest.MAX_PAGE_SIZE));
    }

    public TableSchemaSemanticPrompt getTableSchema(
            String tableName, PageRequest columnPageRequest, PageRequest relationPageRequest) {
        Datasource datasource = getActiveDatasource();
        if (datasource == null) {
            throw new SemanticSchemaException(
                    "No active datasource available, cannot get table schema.");
        }
        VisibilityContext visibilityContext =
                semanticVisibilitySupport.createVisibilityContext(datasource);
        TableMergeSnapshot tableSnapshot = visibilityContext.findMergedTable(tableName);
        if (tableSnapshot == null || tableSnapshot.physicalTable() == null) {
            throw new SemanticSchemaException("Table " + tableName + " does not exist.");
        }

        if (tableSnapshot != null
                && !isTableVisible(tableSnapshot.semanticTable(), tableSnapshot.physicalTable())) {
            throw new SemanticSchemaException(
                    "Table " + tableName + " is not visible in semantic metadata.");
        }

        String resolvedTableName = resolveCanonicalTableName(tableSnapshot);
        List<ColumnMergeSnapshot> columns = visibilityContext.mergeColumns(resolvedTableName);
        if (columns.isEmpty()) {
            throw new SemanticSchemaException(
                    "Table " + tableName + " does not exist or has no column information.");
        }

        TableInfo physicalTable = tableSnapshot != null ? tableSnapshot.physicalTable() : null;
        TableInfo semanticTable = tableSnapshot != null ? tableSnapshot.semanticTable() : null;

        return new TableSchemaSemanticPrompt(
                resolvedTableName,
                resolveTableDomain(physicalTable, semanticTable),
                resolveTableSemantic(physicalTable, semanticTable),
                paginateMapped(
                        columns,
                        columnPageRequest,
                        snapshot ->
                                isColumnVisible(
                                        snapshot.semanticColumn(), snapshot.physicalColumn()),
                        this::mapColumnPrompt),
                paginateMapped(
                        mergeVisibleRelations(visibilityContext, datasource, resolvedTableName),
                        relationPageRequest,
                        relation -> true,
                        Function.identity()));
    }

    private List<TableSemanticResponse> mapTableResponses(List<TableMergeSnapshot> tables) {
        return tables.stream().map(this::mapTableResponse).toList();
    }

    private List<ColumnSemanticResponse> mapColumnResponses(List<ColumnMergeSnapshot> columns) {
        return columns.stream().map(this::mapColumnResponse).toList();
    }

    private TableSemanticResponse mapTableResponse(TableMergeSnapshot snapshot) {
        TableInfo physicalTable = snapshot.physicalTable();
        TableInfo semanticTable = snapshot.semanticTable();
        Integer id = semanticTable != null ? semanticTable.getId() : null;
        String physicalDescription =
                physicalTable != null ? physicalTable.getTableDescription() : null;
        String semanticDescription =
                semanticTable != null ? semanticTable.getTableDescription() : null;
        Boolean isVisible =
                semanticTable != null
                        ? semanticTable.getIsVisible()
                        : physicalTable != null ? physicalTable.getIsVisible() : null;
        LocalDateTime updateTime =
                semanticTable != null
                        ? semanticTable.getUpdateTime()
                        : physicalTable != null ? physicalTable.getUpdateTime() : null;
        return new TableSemanticResponse(
                id,
                resolveCanonicalTableName(snapshot),
                resolveTableDomain(physicalTable, semanticTable),
                physicalDescription,
                semanticDescription,
                isVisible,
                updateTime);
    }

    private ColumnSemanticResponse mapColumnResponse(ColumnMergeSnapshot snapshot) {
        ColumnInfo physicalColumn = snapshot.physicalColumn();
        ColumnSemanticInfo semanticColumn = snapshot.semanticColumn();
        return new ColumnSemanticResponse(
                semanticColumn != null ? semanticColumn.getId() : null,
                physicalColumn.getColumnName(),
                physicalColumn.getRemarks(),
                semanticColumn != null ? semanticColumn.getColumnDescription() : null,
                physicalColumn.getTypeName(),
                semanticColumn != null ? semanticColumn.getIsVisible() : true,
                semanticColumn != null ? semanticColumn.getUpdateTime() : null,
                physicalColumn.isPrimaryKey());
    }

    private ColumnSemanticPrompt mapColumnPrompt(
            ColumnInfo physicalColumn, ColumnSemanticInfo semanticColumn) {
        return new ColumnSemanticPrompt(
                physicalColumn.getColumnName(),
                buildTypeText(physicalColumn),
                physicalColumn.isPrimaryKey(),
                physicalColumn.isNullable(),
                normalizeBlankToNull(physicalColumn.getDefaultValue()),
                resolveColumnSemantic(physicalColumn, semanticColumn));
    }

    private ColumnSemanticPrompt mapColumnPrompt(ColumnMergeSnapshot snapshot) {
        return mapColumnPrompt(snapshot.physicalColumn(), snapshot.semanticColumn());
    }

    private List<TableRelationToolResponse> mergeVisibleRelations(
            SemanticVisibilitySupport.VisibilityContext visibilityContext,
            Datasource datasource,
            String tableName) {
        LinkedHashMap<String, TableRelationToolResponse> mergedRelations = new LinkedHashMap<>();

        for (TableRelationInfo relationInfo :
                schemaReader.getImportedRelations(datasource, tableName)) {
            TableRelationToolResponse relationResponse =
                    mapPhysicalRelation(visibilityContext, relationInfo, tableName);
            if (relationResponse == null) {
                continue;
            }
            mergedRelations.put(buildRelationKey(relationResponse), relationResponse);
        }

        for (LogicalTableRelation logicalRelation :
                logicalTableRelationService.listEnabledByDatasourceIdAndSourceTable(
                        datasource.getId(), tableName)) {
            TableRelationToolResponse relationResponse =
                    mapLogicalRelation(visibilityContext, logicalRelation, tableName);
            if (relationResponse == null) {
                continue;
            }
            mergedRelations.put(buildRelationKey(relationResponse), relationResponse);
        }

        return new ArrayList<>(mergedRelations.values());
    }

    private TableRelationToolResponse mapPhysicalRelation(
            SemanticVisibilitySupport.VisibilityContext visibilityContext,
            TableRelationInfo relationInfo,
            String requestedTableName) {
        if (!isRelationEndpointVisible(
                visibilityContext,
                relationInfo.sourceTableName(),
                relationInfo.sourceColumnNames(),
                relationInfo.targetTableName(),
                relationInfo.targetColumnNames())) {
            return null;
        }
        return new TableRelationToolResponse(
                relationInfo.relationType(),
                LogicalTableRelationSupport.RELATION_SOURCE_PHYSICAL,
                requestedTableName,
                relationInfo.sourceColumnNames(),
                relationInfo.targetTableName(),
                relationInfo.targetColumnNames(),
                relationInfo.description());
    }

    private TableRelationToolResponse mapLogicalRelation(
            SemanticVisibilitySupport.VisibilityContext visibilityContext,
            LogicalTableRelation relation,
            String requestedTableName) {
        List<String> sourceColumnNames;
        List<String> targetColumnNames;
        try {
            sourceColumnNames =
                    logicalTableRelationSupport.fromJson(
                            relation.getSourceColumnNamesJson(), "sourceColumnNames");
            targetColumnNames =
                    logicalTableRelationSupport.fromJson(
                            relation.getTargetColumnNamesJson(), "targetColumnNames");
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Skipping invalid logical relation {} while building table schema for {}: {}",
                    relation.getId(),
                    requestedTableName,
                    e.getMessage());
            return null;
        }
        if (!Boolean.TRUE.equals(relation.getIsEnabled())
                || !isRelationEndpointVisible(
                        visibilityContext,
                        relation.getSourceTableName(),
                        sourceColumnNames,
                        relation.getTargetTableName(),
                        targetColumnNames)) {
            return null;
        }
        return new TableRelationToolResponse(
                relation.getRelationType() != null
                        ? relation.getRelationType()
                        : LogicalTableRelationSupport.RELATION_TYPE_FOREIGN_KEY,
                LogicalTableRelationSupport.RELATION_SOURCE_LOGICAL,
                requestedTableName,
                sourceColumnNames,
                relation.getTargetTableName(),
                targetColumnNames,
                normalizeBlankToNull(relation.getDescription()));
    }

    private boolean isRelationEndpointVisible(
            SemanticVisibilitySupport.VisibilityContext visibilityContext,
            String sourceTableName,
            List<String> sourceColumnNames,
            String targetTableName,
            List<String> targetColumnNames) {
        if (!isVisibleTable(visibilityContext, sourceTableName)
                || !isVisibleTable(visibilityContext, targetTableName)) {
            return false;
        }
        return areVisibleColumns(visibilityContext, sourceTableName, sourceColumnNames)
                && areVisibleColumns(visibilityContext, targetTableName, targetColumnNames);
    }

    private boolean isVisibleTable(
            SemanticVisibilitySupport.VisibilityContext visibilityContext, String tableName) {
        SemanticVisibilitySupport.TableMergeSnapshot snapshot =
                visibilityContext.findMergedTable(tableName);
        return snapshot != null
                && semanticVisibilitySupport.isTableVisible(
                        snapshot.semanticTable(), snapshot.physicalTable());
    }

    private boolean areVisibleColumns(
            SemanticVisibilitySupport.VisibilityContext visibilityContext,
            String tableName,
            List<String> columnNames) {
        for (String columnName : columnNames) {
            SemanticVisibilitySupport.ColumnMergeSnapshot snapshot =
                    visibilityContext.findMergedColumn(tableName, columnName);
            if (snapshot == null
                    || snapshot.physicalColumn() == null
                    || !semanticVisibilitySupport.isColumnVisible(
                            snapshot.semanticColumn(), snapshot.physicalColumn())) {
                return false;
            }
        }
        return true;
    }

    private String buildRelationKey(TableRelationToolResponse relationResponse) {
        return normalizeRelationName(relationResponse.sourceTableName())
                + "|"
                + normalizeRelationColumns(relationResponse.sourceColumnNames())
                + "|"
                + normalizeRelationName(relationResponse.targetTableName())
                + "|"
                + normalizeRelationColumns(relationResponse.targetColumnNames());
    }

    private String normalizeRelationName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRelationColumns(List<String> columnNames) {
        return columnNames.stream()
                .map(columnName -> columnName.trim().toLowerCase(Locale.ROOT))
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private String resolveColumnSemantic(
            ColumnInfo physicalColumn, ColumnSemanticInfo semanticColumn) {
        if (semanticColumn != null
                && semanticColumn.getColumnDescription() != null
                && !semanticColumn.getColumnDescription().isBlank()) {
            return semanticColumn.getColumnDescription().trim();
        }
        if (physicalColumn.getRemarks() != null && !physicalColumn.getRemarks().isBlank()) {
            return physicalColumn.getRemarks().trim();
        }
        return "No semantic description is configured for this column.";
    }

    private String resolveTableDomain(TableInfo physicalTable, TableInfo semanticTable) {
        if (semanticTable != null
                && semanticTable.getDomain() != null
                && !semanticTable.getDomain().isBlank()) {
            return semanticTable.getDomain().trim();
        }
        if (physicalTable != null
                && physicalTable.getDomain() != null
                && !physicalTable.getDomain().isBlank()) {
            return physicalTable.getDomain().trim();
        }
        return null;
    }

    private String resolveTableSemantic(TableInfo physicalTable, TableInfo semanticTable) {
        if (semanticTable != null
                && semanticTable.getTableDescription() != null
                && !semanticTable.getTableDescription().isBlank()) {
            return semanticTable.getTableDescription().trim();
        }
        if (physicalTable != null
                && physicalTable.getTableDescription() != null
                && !physicalTable.getTableDescription().isBlank()) {
            return physicalTable.getTableDescription().trim();
        }
        return "";
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String resolveCanonicalTableName(TableMergeSnapshot snapshot) {
        return semanticVisibilitySupport.resolveTableName(snapshot);
    }

    private TableMergeSnapshot requirePhysicalTableSnapshot(
            Datasource datasource, String tableName) {
        return requirePhysicalTableSnapshot(
                semanticVisibilitySupport.createVisibilityContext(datasource), tableName);
    }

    private TableMergeSnapshot requirePhysicalTableSnapshot(
            VisibilityContext visibilityContext, String tableName) {
        TableMergeSnapshot tableSnapshot = visibilityContext.findMergedTable(tableName);
        if (tableSnapshot == null || tableSnapshot.physicalTable() == null) {
            return null;
        }
        return tableSnapshot;
    }

    private TableInfo findPreferredSemanticTable(Integer datasourceId, String tableName) {
        TableInfo preferred = null;
        for (TableInfo tableInfo : findMatchingSemanticTables(datasourceId, tableName)) {
            preferred = selectPreferredSemanticTable(preferred, tableInfo);
        }
        return preferred;
    }

    private List<TableInfo> findMatchingSemanticTables(Integer datasourceId, String tableName) {
        return tableInfoService.findByDatasourceId(datasourceId).stream()
                .filter(
                        tableInfo ->
                                logicalTableRelationSupport.sameTableName(
                                        tableInfo.getTableName(), tableName))
                .toList();
    }

    private TableInfo selectPreferredSemanticTable(TableInfo existing, TableInfo candidate) {
        if (existing == null) {
            return candidate;
        }
        LocalDateTime existingUpdateTime =
                existing.getUpdateTime() != null
                        ? existing.getUpdateTime()
                        : existing.getCreateTime();
        LocalDateTime candidateUpdateTime =
                candidate.getUpdateTime() != null
                        ? candidate.getUpdateTime()
                        : candidate.getCreateTime();
        if (candidateUpdateTime == null) {
            return existing;
        }
        if (existingUpdateTime == null || candidateUpdateTime.isAfter(existingUpdateTime)) {
            return candidate;
        }
        return existing;
    }

    private ColumnSemanticInfo findPreferredSemanticColumn(
            Integer datasourceId, String tableName, String columnName) {
        ColumnSemanticInfo preferred = null;
        for (ColumnSemanticInfo columnSemanticInfo :
                findMatchingSemanticColumns(datasourceId, tableName, columnName)) {
            preferred = selectPreferredSemanticColumn(preferred, columnSemanticInfo);
        }
        return preferred;
    }

    private List<ColumnSemanticInfo> findMatchingSemanticColumns(
            Integer datasourceId, String tableName, String columnName) {
        String normalizedColumnName = normalizeIdentifierKey(columnName);
        return columnSemanticInfoService.findByDatasourceId(datasourceId).stream()
                .filter(
                        semanticColumn ->
                                logicalTableRelationSupport.sameTableName(
                                        semanticColumn.getTableName(), tableName))
                .filter(
                        semanticColumn ->
                                normalizedColumnName.equals(
                                        normalizeIdentifierKey(semanticColumn.getColumnName())))
                .toList();
    }

    private ColumnSemanticInfo selectPreferredSemanticColumn(
            ColumnSemanticInfo existing, ColumnSemanticInfo candidate) {
        if (existing == null) {
            return candidate;
        }
        LocalDateTime existingUpdateTime =
                existing.getUpdateTime() != null
                        ? existing.getUpdateTime()
                        : existing.getCreateTime();
        LocalDateTime candidateUpdateTime =
                candidate.getUpdateTime() != null
                        ? candidate.getUpdateTime()
                        : candidate.getCreateTime();
        if (candidateUpdateTime == null) {
            return existing;
        }
        if (existingUpdateTime == null || candidateUpdateTime.isAfter(existingUpdateTime)) {
            return candidate;
        }
        return existing;
    }

    private String buildTypeText(ColumnInfo physicalColumn) {
        StringBuilder sb = new StringBuilder();
        sb.append(physicalColumn.getTypeName());
        if (physicalColumn.getColumnSize() > 0) {
            sb.append("(").append(physicalColumn.getColumnSize()).append(")");
        }
        return sb.toString();
    }

    private boolean isTableVisible(TableInfo semanticTable, TableInfo physicalTable) {
        return semanticVisibilitySupport.isTableVisible(semanticTable, physicalTable);
    }

    private boolean isColumnVisible(ColumnSemanticInfo semanticColumn, ColumnInfo physicalColumn) {
        return semanticVisibilitySupport.isColumnVisible(semanticColumn, physicalColumn);
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
        List<T> pageItems = new ArrayList<>();
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

    private Datasource requireDatasource(Integer datasourceId) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
        return datasource;
    }

    private Set<String> normalizeIdentifierKeys(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
        LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " contains a blank value.");
            }
            normalizedValues.add(normalizeIdentifierKey(value));
        }
        return normalizedValues;
    }

    private String normalizeIdentifierKey(String value) {
        return logicalTableRelationSupport.normalizeIdentifierKey(value);
    }

    private Datasource getActiveDatasource() {
        return activeDatasourceSupport.getActiveDatasource();
    }

    public static class SemanticSchemaException extends RuntimeException {
        public SemanticSchemaException(String message) {
            super(message);
        }
    }
}

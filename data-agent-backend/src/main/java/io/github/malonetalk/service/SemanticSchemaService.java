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
import io.github.malonetalk.common.StatusConstants;
import io.github.malonetalk.dto.semantic.ColumnSemanticResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticUpdateRequest;
import io.github.malonetalk.dto.semantic.TableSchemaSemanticPrompt;
import io.github.malonetalk.dto.semantic.TableSemanticResponse;
import io.github.malonetalk.dto.semantic.TableSemanticUpdateRequest;
import io.github.malonetalk.dto.toolresponse.ColumnSemanticPrompt;
import io.github.malonetalk.dto.toolresponse.TableSemanticPrompt;
import io.github.malonetalk.entity.ColumnSemanticInfo;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.TableInfo;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SemanticSchemaService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticSchemaService.class);

    private final DatasourceService datasourceService;
    private final TableInfoService tableInfoService;
    private final ColumnSemanticInfoService columnSemanticInfoService;
    private final SchemaReader schemaReader;

    public SemanticSchemaService(
            DatasourceService datasourceService,
            TableInfoService tableInfoService,
            ColumnSemanticInfoService columnSemanticInfoService,
            SchemaReader schemaReader) {
        this.datasourceService = datasourceService;
        this.tableInfoService = tableInfoService;
        this.columnSemanticInfoService = columnSemanticInfoService;
        this.schemaReader = schemaReader;
    }

    public List<TableSemanticResponse> getAllTables() {
        Datasource datasource = getActiveDatasource();
        if (datasource == null) {
            return Collections.emptyList();
        }
        return mapTableResponses(mergeTables(datasource));
    }

    public List<TableSemanticResponse> getAllTables(Integer datasourceId) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return Collections.emptyList();
        }
        return mapTableResponses(mergeTables(datasource));
    }

    public List<TableSemanticPrompt> getVisibleTablePrompts() {
        Datasource datasource = getActiveDatasource();
        if (datasource == null) {
            return Collections.emptyList();
        }
        return mergeTables(datasource).stream()
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

    public boolean updateTableSemantic(TableSemanticUpdateRequest request) {
        Datasource datasource = datasourceService.findById(request.datasourceId());
        if (datasource == null) {
            return false;
        }

        TableInfo physicalTable =
                schemaReader.getTables(datasource).stream()
                        .filter(item -> request.tableName().equals(item.getTableName()))
                        .findFirst()
                        .orElse(null);
        if (physicalTable == null) {
            return false;
        }

        TableInfo tableInfo =
                tableInfoService.findByDatasourceIdAndTableName(
                        request.datasourceId(), request.tableName());
        if (tableInfo != null) {
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
        semanticTable.setTableName(physicalTable.getTableName());
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
        TableInfo physicalTable =
                schemaReader.getTables(datasource).stream()
                        .filter(item -> tableName.equals(item.getTableName()))
                        .findFirst()
                        .orElse(null);
        if (physicalTable == null) {
            return false;
        }
        return tableInfoService.deleteByDatasourceIdAndTableName(datasourceId, tableName);
    }

    public List<ColumnSemanticResponse> getAllColumns(Integer datasourceId, String tableName) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return Collections.emptyList();
        }
        return mapColumnResponses(mergeColumns(datasource, tableName));
    }

    public boolean updateColumnSemantic(
            Integer datasourceId, String tableName, ColumnSemanticUpdateRequest request) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return false;
        }

        ColumnInfo physicalColumn =
                schemaReader.getTableSchema(datasource, tableName).stream()
                        .filter(item -> request.columnName().equals(item.getColumnName()))
                        .findFirst()
                        .orElse(null);
        if (physicalColumn == null) {
            return false;
        }

        ColumnSemanticInfo columnSemanticInfo =
                columnSemanticInfoService.findByDatasourceIdAndTableNameAndColumnName(
                        datasourceId, tableName, request.columnName());
        if (columnSemanticInfo != null) {
            if (request.columnDescription() != null) {
                columnSemanticInfo.setColumnDescription(request.columnDescription());
            }
            columnSemanticInfo.setIsVisible(request.isVisible());
            return columnSemanticInfoService.update(columnSemanticInfo);
        }

        ColumnSemanticInfo semanticColumn = new ColumnSemanticInfo();
        semanticColumn.setDatasourceId(datasourceId);
        semanticColumn.setTableName(tableName);
        semanticColumn.setColumnName(physicalColumn.getColumnName());
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
        ColumnInfo physicalColumn =
                schemaReader.getTableSchema(datasource, tableName).stream()
                        .filter(item -> columnName.equals(item.getColumnName()))
                        .findFirst()
                        .orElse(null);
        if (physicalColumn == null) {
            return false;
        }
        return columnSemanticInfoService.deleteByDatasourceIdAndTableNameAndColumnName(
                datasourceId, tableName, columnName);
    }

    public List<ColumnSemanticPrompt> getVisibleColumnPrompts(
            Integer datasourceId, String tableName) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return Collections.emptyList();
        }
        return mergeColumns(datasource, tableName).stream()
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
        Datasource datasource = getActiveDatasource();
        if (datasource == null) {
            throw new SemanticSchemaException(
                    "No active datasource available, cannot get table schema.");
        }

        TableMergeSnapshot tableSnapshot =
                mergeTables(datasource).stream()
                        .filter(
                                snapshot ->
                                        (snapshot.semanticTable() != null
                                                        && tableName.equals(
                                                                snapshot.semanticTable()
                                                                        .getTableName()))
                                                || (snapshot.physicalTable() != null
                                                        && tableName.equals(
                                                                snapshot.physicalTable()
                                                                        .getTableName())))
                        .findFirst()
                        .orElse(null);

        if (tableSnapshot != null
                && !isTableVisible(tableSnapshot.semanticTable(), tableSnapshot.physicalTable())) {
            throw new SemanticSchemaException(
                    "Table " + tableName + " is not visible in semantic metadata.");
        }

        List<ColumnMergeSnapshot> columns = mergeColumns(datasource, tableName);
        if (columns.isEmpty()) {
            throw new SemanticSchemaException(
                    "Table " + tableName + " does not exist or has no column information.");
        }

        TableInfo physicalTable = tableSnapshot != null ? tableSnapshot.physicalTable() : null;
        TableInfo semanticTable = tableSnapshot != null ? tableSnapshot.semanticTable() : null;
        String resolvedTableName =
                semanticTable != null ? semanticTable.getTableName() : physicalTable.getTableName();

        List<ColumnSemanticPrompt> visibleColumns =
                columns.stream()
                        .filter(
                                snapshot ->
                                        isColumnVisible(
                                                snapshot.semanticColumn(),
                                                snapshot.physicalColumn()))
                        .map(
                                snapshot ->
                                        mapColumnPrompt(
                                                snapshot.physicalColumn(),
                                                snapshot.semanticColumn()))
                        .toList();

        return new TableSchemaSemanticPrompt(
                resolvedTableName,
                resolveTableDomain(physicalTable, semanticTable),
                resolveTableSemantic(physicalTable, semanticTable),
                visibleColumns);
    }

    private List<TableMergeSnapshot> mergeTables(Datasource datasource) {
        List<TableInfo> physicalTables = schemaReader.getTables(datasource);
        List<TableInfo> semanticTables = tableInfoService.findByDatasourceId(datasource.getId());
        LinkedHashMap<String, TableMergeSnapshot> mergedTables = new LinkedHashMap<>();

        for (TableInfo physicalTable : physicalTables) {
            mergedTables.put(
                    physicalTable.getTableName(), new TableMergeSnapshot(physicalTable, null));
        }

        for (TableInfo semanticTable : semanticTables) {
            TableMergeSnapshot existing = mergedTables.get(semanticTable.getTableName());
            if (existing == null) {
                continue;
            }
            TableInfo preferredSemanticTable =
                    selectPreferredSemanticTable(existing.semanticTable(), semanticTable);
            mergedTables.put(
                    semanticTable.getTableName(),
                    new TableMergeSnapshot(existing.physicalTable(), preferredSemanticTable));
        }

        return new ArrayList<>(mergedTables.values());
    }

    private List<ColumnMergeSnapshot> mergeColumns(Datasource datasource, String tableName) {
        List<ColumnInfo> physicalColumns = schemaReader.getTableSchema(datasource, tableName);
        List<ColumnSemanticInfo> semanticColumns =
                columnSemanticInfoService.findByDatasourceIdAndTableName(
                        datasource.getId(), tableName);
        LinkedHashMap<String, ColumnMergeSnapshot> mergedColumns = new LinkedHashMap<>();

        for (ColumnInfo physicalColumn : physicalColumns) {
            mergedColumns.put(
                    physicalColumn.getColumnName(), new ColumnMergeSnapshot(physicalColumn, null));
        }

        for (ColumnSemanticInfo semanticColumn : semanticColumns) {
            ColumnMergeSnapshot existing = mergedColumns.get(semanticColumn.getColumnName());
            if (existing == null) {
                continue;
            }
            mergedColumns.put(
                    semanticColumn.getColumnName(),
                    new ColumnMergeSnapshot(existing.physicalColumn(), semanticColumn));
        }

        return new ArrayList<>(mergedColumns.values());
    }

    private List<TableSemanticResponse> mapTableResponses(List<TableMergeSnapshot> tables) {
        return tables.stream()
                .map(
                        snapshot -> {
                            TableInfo physicalTable = snapshot.physicalTable();
                            TableInfo semanticTable = snapshot.semanticTable();
                            Integer id = semanticTable != null ? semanticTable.getId() : null;
                            String tableName =
                                    semanticTable != null
                                            ? semanticTable.getTableName()
                                            : physicalTable.getTableName();
                            String physicalDescription =
                                    physicalTable != null
                                            ? physicalTable.getTableDescription()
                                            : null;
                            String semanticDescription =
                                    semanticTable != null
                                            ? semanticTable.getTableDescription()
                                            : null;
                            Boolean isVisible =
                                    semanticTable != null
                                            ? semanticTable.getIsVisible()
                                            : physicalTable != null
                                                    ? physicalTable.getIsVisible()
                                                    : null;
                            LocalDateTime updateTime =
                                    semanticTable != null
                                            ? semanticTable.getUpdateTime()
                                            : physicalTable != null
                                                    ? physicalTable.getUpdateTime()
                                                    : null;
                            return new TableSemanticResponse(
                                    id,
                                    tableName,
                                    resolveTableDomain(physicalTable, semanticTable),
                                    physicalDescription,
                                    semanticDescription,
                                    isVisible,
                                    updateTime);
                        })
                .toList();
    }

    private List<ColumnSemanticResponse> mapColumnResponses(List<ColumnMergeSnapshot> columns) {
        return columns.stream()
                .map(
                        snapshot -> {
                            ColumnInfo physicalColumn = snapshot.physicalColumn();
                            ColumnSemanticInfo semanticColumn = snapshot.semanticColumn();
                            return new ColumnSemanticResponse(
                                    semanticColumn != null ? semanticColumn.getId() : null,
                                    physicalColumn.getColumnName(),
                                    physicalColumn.getRemarks(),
                                    semanticColumn != null
                                            ? semanticColumn.getColumnDescription()
                                            : null,
                                    physicalColumn.getTypeName(),
                                    semanticColumn != null ? semanticColumn.getIsVisible() : true,
                                    semanticColumn != null ? semanticColumn.getUpdateTime() : null,
                                    physicalColumn.isPrimaryKey());
                        })
                .toList();
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

    private String buildTypeText(ColumnInfo physicalColumn) {
        StringBuilder sb = new StringBuilder();
        sb.append(physicalColumn.getTypeName());
        if (physicalColumn.getColumnSize() > 0) {
            sb.append("(").append(physicalColumn.getColumnSize()).append(")");
        }
        return sb.toString();
    }

    private boolean isTableVisible(TableInfo semanticTable, TableInfo physicalTable) {
        if (semanticTable != null) {
            return Boolean.TRUE.equals(semanticTable.getIsVisible())
                    && Boolean.TRUE.equals(semanticTable.getIsActive());
        }
        return physicalTable != null && Boolean.TRUE.equals(physicalTable.getIsVisible());
    }

    private boolean isColumnVisible(ColumnSemanticInfo semanticColumn, ColumnInfo physicalColumn) {
        if (semanticColumn != null) {
            return Boolean.TRUE.equals(semanticColumn.getIsVisible())
                    && Boolean.TRUE.equals(semanticColumn.getIsActive());
        }
        return physicalColumn != null;
    }

    private Datasource getActiveDatasource() {
        List<Datasource> activeDataSources = datasourceService.findByStatus(StatusConstants.ACTIVE);
        if (activeDataSources.isEmpty()) {
            return null;
        }
        if (activeDataSources.size() > 1) {
            logger.warn(
                    "Found {} active data sources, using the first one with id={}.",
                    activeDataSources.size(),
                    activeDataSources.get(0).getId());
        }
        return activeDataSources.get(0);
    }

    private record TableMergeSnapshot(TableInfo physicalTable, TableInfo semanticTable) {}

    private record ColumnMergeSnapshot(
            ColumnInfo physicalColumn, ColumnSemanticInfo semanticColumn) {}

    public static class SemanticSchemaException extends RuntimeException {
        public SemanticSchemaException(String message) {
            super(message);
        }
    }
}

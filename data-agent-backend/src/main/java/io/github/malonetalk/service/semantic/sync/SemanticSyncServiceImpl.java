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
package io.github.malonetalk.service.semantic.sync;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.dto.datasource.PhysicalColumnInfo;
import io.github.malonetalk.dto.datasource.PhysicalTableInfo;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.PhysicalTableCandidatePageQuery;
import io.github.malonetalk.dto.semantic.PhysicalTableCandidateResponse;
import io.github.malonetalk.dto.semantic.RefreshPhysicalStatusRequest;
import io.github.malonetalk.dto.semantic.SyncTableResult;
import io.github.malonetalk.dto.semantic.SyncTableSemanticsRequest;
import io.github.malonetalk.dto.semantic.SyncTableSemanticsResponse;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class SemanticSyncServiceImpl implements SemanticSyncService {

    private final DatasourceService datasourceService;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final SchemaReader schemaReader;
    private final SemanticSyncApplyService semanticSyncApplyService;
    private final SemanticSyncResultService semanticSyncResultService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PageResponse<PhysicalTableCandidateResponse> getPhysicalTableCandidates(
            PhysicalTableCandidatePageQuery query) {
        Datasource datasource = requireDatasource(query.datasourceId());
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        List<PhysicalTableInfo> physicalTables = schemaReader.getTables(datasource);
        Map<String, TableInfo> semanticTables =
                tableInfoMapper.selectByDatasourceId(query.datasourceId()).stream()
                        .collect(
                                Collectors.toMap(
                                        table ->
                                                SemanticUtils.normalizeObjectName(
                                                        table.getTableName(),
                                                        "Missing tableName while indexing synced"
                                                                + " semantic tables."),
                                        table -> table,
                                        (left, _right) -> left,
                                        LinkedHashMap::new));
        String keyword = SemanticUtils.trimToNull(query.keyword());
        boolean sortDescending = SemanticUtils.isDescendingSort(query.sortOrder());

        List<PhysicalTableCandidateResponse> filtered =
                physicalTables.stream()
                        .filter(table -> matchesKeyword(table, keyword))
                        .sorted(buildTableComparator(sortDescending))
                        .map(
                                table ->
                                        new PhysicalTableCandidateResponse(
                                                table.tableName(),
                                                SemanticUtils.trimToNull(table.remarks()),
                                                semanticTables.containsKey(
                                                        SemanticUtils.normalizeObjectName(
                                                                table.tableName(),
                                                                "Missing physical tableName while"
                                                                        + " listing table"
                                                                        + " candidates."))))
                        .toList();

        int fromIndex = Math.min((pageNumber - 1) * pageSize, filtered.size());
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        return PageResponse.of(
                filtered.subList(fromIndex, toIndex), filtered.size(), pageNumber, pageSize);
    }

    @Override
    @Transactional
    public SyncTableSemanticsResponse syncTables(SyncTableSemanticsRequest request) {
        Datasource datasource = requireDatasource(request.datasourceId());
        Map<String, PhysicalTableInfo> physicalTables =
                schemaReader.getTables(datasource).stream()
                        .collect(
                                Collectors.toMap(
                                        table ->
                                                SemanticUtils.normalizeObjectName(
                                                        table.tableName(),
                                                        "Missing physical tableName while loading"
                                                                + " tables for semantic sync."),
                                        table -> table,
                                        (left, _right) -> left,
                                        LinkedHashMap::new));
        Set<String> selectedTableNames =
                request.tableNames().stream()
                        .map(
                                name ->
                                        SemanticUtils.normalizeObjectName(
                                                name,
                                                "Missing tableName for selected table semantic"
                                                        + " sync."))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        List<SyncTableResult> results = new ArrayList<>();
        for (String normalizedTableName : selectedTableNames) {
            PhysicalTableInfo physicalTable = physicalTables.get(normalizedTableName);
            SyncTableResult result =
                    physicalTable == null
                            ? semanticSyncApplyService.markMissingTable(
                                    request.datasourceId(),
                                    normalizedTableName,
                                    LocalDateTime.now())
                            : syncSingleTable(
                                    datasource,
                                    request.datasourceId(),
                                    physicalTable,
                                    normalizedTableName);
            results.add(result);
        }

        return semanticSyncResultService.summarize(results);
    }

    @Override
    public SyncTableSemanticsResponse refreshPhysicalStatus(RefreshPhysicalStatusRequest request) {
        Datasource datasource = requireDatasource(request.datasourceId());
        Map<String, PhysicalTableInfo> physicalTables =
                schemaReader.getTables(datasource).stream()
                        .collect(
                                Collectors.toMap(
                                        table ->
                                                SemanticUtils.normalizeObjectName(
                                                        table.tableName(),
                                                        "Missing physical tableName while loading"
                                                                + " tables for physical status"
                                                                + " refresh."),
                                        table -> table,
                                        (left, _right) -> left,
                                        LinkedHashMap::new));
        Map<String, Set<String>> physicalColumnNamesByTable =
                loadPhysicalColumnNamesByTable(datasource, physicalTables);
        int pageNumber = 1;
        int pageSize = PageResponse.resolvePageSize(100);
        List<SyncTableResult> results = new ArrayList<>();

        while (true) {
            PhysicalStatusPageRefresh pageRefresh =
                    refreshPhysicalStatusPage(
                            request.datasourceId(),
                            physicalTables,
                            physicalColumnNamesByTable,
                            pageNumber,
                            pageSize);
            if (!pageRefresh.hasRows()) {
                break;
            }
            results.addAll(pageRefresh.results());
            if (pageRefresh.pageNumber() >= pageRefresh.pageCount()) {
                break;
            }
            pageNumber++;
        }

        return semanticSyncResultService.summarize(results);
    }

    private PhysicalStatusPageRefresh refreshPhysicalStatusPage(
            Integer datasourceId,
            Map<String, PhysicalTableInfo> physicalTables,
            Map<String, Set<String>> physicalColumnNamesByTable,
            int pageNumber,
            int pageSize) {
        return transactionTemplate.execute(
                status -> {
                    PageHelper.startPage(pageNumber, pageSize);
                    Page<TableInfo> page =
                            (Page<TableInfo>) tableInfoMapper.selectByDatasourceId(datasourceId);
                    if (page.isEmpty()) {
                        return new PhysicalStatusPageRefresh(
                                false, page.getPageNum(), page.getPages(), List.of());
                    }
                    Map<String, List<ColumnInfo>> semanticColumnsByTable =
                            loadSemanticColumnsByTable(datasourceId, page, physicalTables);
                    List<SyncTableResult> results =
                            refreshPhysicalStatusForTables(
                                    datasourceId,
                                    page,
                                    physicalTables,
                                    physicalColumnNamesByTable,
                                    semanticColumnsByTable);
                    return new PhysicalStatusPageRefresh(
                            true, page.getPageNum(), page.getPages(), results);
                });
    }

    private List<SyncTableResult> refreshPhysicalStatusForTables(
            Integer datasourceId,
            List<TableInfo> tableInfos,
            Map<String, PhysicalTableInfo> physicalTables,
            Map<String, Set<String>> physicalColumnNamesByTable,
            Map<String, List<ColumnInfo>> semanticColumnsByTable) {
        List<SyncTableResult> results = new ArrayList<>();
        for (TableInfo tableInfo : tableInfos) {
            String normalizedTableName =
                    SemanticUtils.normalizeObjectName(
                            tableInfo.getTableName(),
                            "Missing tableName while refreshing physical status.");
            PhysicalTableInfo physicalTable = physicalTables.get(normalizedTableName);
            if (physicalTable == null) {
                results.add(
                        semanticSyncApplyService.markMissingTable(
                                datasourceId, normalizedTableName, LocalDateTime.now()));
                continue;
            }
            SyncTableResult result =
                    markMissingColumnsForPresentTable(
                            tableInfo,
                            semanticColumnsByTable.getOrDefault(normalizedTableName, List.of()),
                            physicalColumnNamesByTable.getOrDefault(normalizedTableName, Set.of()));
            if (result.missingColumnsMarked() > 0) {
                results.add(result);
            }
        }
        return results;
    }

    private Map<String, Set<String>> loadPhysicalColumnNamesByTable(
            Datasource datasource, Map<String, PhysicalTableInfo> physicalTables) {
        List<String> tableNames =
                physicalTables.values().stream().map(PhysicalTableInfo::tableName).toList();
        return schemaReader.getTableColumnNames(datasource, tableNames).entrySet().stream()
                .collect(
                        Collectors.toMap(
                                entry ->
                                        SemanticUtils.normalizeObjectName(
                                                entry.getKey(),
                                                "Missing tableName while loading physical column"
                                                        + " names."),
                                entry ->
                                        entry.getValue().stream()
                                                .map(
                                                        columnName ->
                                                                SemanticUtils.normalizeObjectName(
                                                                        columnName,
                                                                        "Missing columnName while"
                                                                                + " loading"
                                                                                + " physical"
                                                                                + " column names."))
                                                .collect(
                                                        Collectors.toCollection(
                                                                LinkedHashSet::new)),
                                (left, right) -> {
                                    left.addAll(right);
                                    return left;
                                },
                                LinkedHashMap::new));
    }

    private Map<String, List<ColumnInfo>> loadSemanticColumnsByTable(
            Integer datasourceId,
            List<TableInfo> tableInfos,
            Map<String, PhysicalTableInfo> physicalTables) {
        List<String> tableNames =
                tableInfos.stream()
                        .map(TableInfo::getTableName)
                        .filter(
                                tableName ->
                                        physicalTables.containsKey(
                                                SemanticUtils.normalizeObjectName(
                                                        tableName,
                                                        "Missing tableName while loading semantic"
                                                                + " columns for refresh.")))
                        .distinct()
                        .toList();
        if (tableNames.isEmpty()) {
            return Map.of();
        }
        return columnSemanticInfoMapper
                .selectByDatasourceIdAndTableNames(datasourceId, tableNames)
                .stream()
                .collect(
                        Collectors.groupingBy(
                                column ->
                                        SemanticUtils.normalizeObjectName(
                                                column.getTableName(),
                                                "Missing tableName while grouping semantic"
                                                        + " columns for refresh."),
                                LinkedHashMap::new,
                                Collectors.toList()));
    }

    private SyncTableResult markMissingColumnsForPresentTable(
            TableInfo tableInfo,
            List<ColumnInfo> semanticColumns,
            Set<String> physicalColumnNames) {
        LocalDateTime now = LocalDateTime.now();
        int missingColumnsMarked =
                semanticSyncApplyService.markMissingColumns(
                        semanticColumns, physicalColumnNames, now);
        return SyncTableResult.builder()
                .tableName(tableInfo.getTableName())
                .physicalTableFound(true)
                .missingColumnsMarked(missingColumnsMarked)
                .message("Physical column status refreshed")
                .build();
    }

    private SyncTableResult syncSingleTable(
            Datasource datasource,
            Integer datasourceId,
            PhysicalTableInfo physicalTable,
            String normalizedTableName) {
        LocalDateTime now = LocalDateTime.now();
        SemanticSyncApplyService.TableSyncDiff tableSyncDiff =
                semanticSyncApplyService.ensureTablePresent(
                        datasourceId,
                        normalizedTableName,
                        SemanticUtils.trimToNull(physicalTable.remarks()),
                        now);

        List<ColumnInfo> semanticColumns =
                columnSemanticInfoMapper.selectByDatasourceIdAndTableName(
                        datasourceId, normalizedTableName);
        Map<String, ColumnInfo> semanticColumnMap =
                semanticColumns.stream()
                        .collect(
                                Collectors.toMap(
                                        column ->
                                                SemanticUtils.normalizeObjectName(
                                                        column.getColumnName(),
                                                        "Missing columnName while indexing"
                                                                + " semantic columns for sync."),
                                        column -> column,
                                        (left, _right) -> left,
                                        LinkedHashMap::new));
        List<PhysicalColumnInfo> physicalColumns =
                schemaReader.getTableSchema(datasource, physicalTable.tableName());
        Set<String> physicalColumnNames = new LinkedHashSet<>();
        int addedColumns = 0;
        int reactivatedColumns = 0;
        int updatedColumns = 0;

        for (PhysicalColumnInfo physicalColumn : physicalColumns) {
            String normalizedColumnName =
                    SemanticUtils.normalizeObjectName(
                            physicalColumn.columnName(),
                            "Missing physical columnName while syncing table.");
            physicalColumnNames.add(normalizedColumnName);
            SemanticSyncApplyService.ColumnSyncDiff columnSyncDiff =
                    semanticSyncApplyService.ensureColumnPresent(
                            datasourceId,
                            normalizedTableName,
                            normalizedColumnName,
                            SemanticUtils.trimToNull(physicalColumn.remarks()),
                            SemanticUtils.trimToNull(physicalColumn.typeName()),
                            physicalColumn.primaryKey(),
                            now,
                            semanticColumnMap);
            if (columnSyncDiff.added()) {
                addedColumns++;
            }
            if (columnSyncDiff.reactivated()) {
                reactivatedColumns++;
            }
            if (columnSyncDiff.updated()) {
                updatedColumns++;
            }
        }

        int missingColumnsMarked =
                semanticSyncApplyService.markMissingColumns(
                        semanticColumns, physicalColumnNames, now);

        return SyncTableResult.builder()
                .tableName(physicalTable.tableName())
                .physicalTableFound(true)
                .tableAdded(tableSyncDiff.added())
                .tableReactivated(tableSyncDiff.reactivated())
                .tableUpdated(tableSyncDiff.updated())
                .addedColumns(addedColumns)
                .reactivatedColumns(reactivatedColumns)
                .updatedColumns(updatedColumns)
                .missingColumnsMarked(missingColumnsMarked)
                .message("同步完成")
                .build();
    }

    private Datasource requireDatasource(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
        return datasource;
    }

    private boolean matchesKeyword(PhysicalTableInfo table, String keyword) {
        if (keyword == null) {
            return true;
        }
        return SemanticUtils.containsIgnoreCase(table.tableName(), keyword)
                || SemanticUtils.containsIgnoreCase(table.remarks(), keyword);
    }

    private Comparator<PhysicalTableInfo> buildTableComparator(boolean sortDescending) {
        Comparator<PhysicalTableInfo> comparator =
                Comparator.comparing(
                        table ->
                                SemanticUtils.normalizeObjectName(
                                        table.tableName(),
                                        "Missing physical tableName while sorting table"
                                                + " candidates."),
                        Comparator.naturalOrder());
        return sortDescending ? comparator.reversed() : comparator;
    }

    private record PhysicalStatusPageRefresh(
            boolean hasRows, int pageNumber, int pageCount, List<SyncTableResult> results) {}
}

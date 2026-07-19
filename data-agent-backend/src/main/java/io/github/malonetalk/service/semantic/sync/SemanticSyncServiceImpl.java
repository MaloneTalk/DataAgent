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
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService.ColumnSyncSource;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService.PhysicalStatusRefreshPlan;
import io.github.malonetalk.service.semantic.sync.SemanticSyncApplyService.TableSyncSource;
import io.github.malonetalk.utils.SemanticUtils;
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
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class SemanticSyncServiceImpl implements SemanticSyncService {

    private final DatasourceService datasourceService;
    private final TableInfoMapper tableInfoMapper;
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
        String keyword = SemanticUtils.trimToNull(query.keyword());
        boolean sortDescending = SemanticUtils.isDescendingSort(query.sortOrder());
        List<PhysicalTableInfo> filteredTables =
                physicalTables.stream()
                        .filter(table -> matchesKeyword(table, keyword))
                        .sorted(buildTableComparator(sortDescending))
                        .toList();
        int fromIndex = Math.min((pageNumber - 1) * pageSize, filteredTables.size());
        int toIndex = Math.min(fromIndex + pageSize, filteredTables.size());
        List<PhysicalTableInfo> pageTables = filteredTables.subList(fromIndex, toIndex);
        Set<String> syncedTableNames = loadSyncedTableNames(query.datasourceId(), pageTables);
        List<PhysicalTableCandidateResponse> responses =
                pageTables.stream()
                        .map(
                                table ->
                                        new PhysicalTableCandidateResponse(
                                                table.tableName(),
                                                SemanticUtils.trimToNull(table.remarks()),
                                                syncedTableNames.contains(
                                                        SemanticUtils.normalizeObjectName(
                                                                table.tableName(),
                                                                "Missing physical tableName while"
                                                                        + " listing table"
                                                                        + " candidates."))))
                        .toList();
        return PageResponse.of(responses, filteredTables.size(), pageNumber, pageSize);
    }

    @Override
    public SyncTableSemanticsResponse syncTables(SyncTableSemanticsRequest request) {
        Datasource datasource = requireDatasource(request.datasourceId());
        Map<String, PhysicalTableInfo> physicalTables =
                loadPhysicalTableIndex(
                        datasource,
                        "Missing physical tableName while loading tables for semantic sync.");
        Set<String> selectedTableNames =
                request.tableNames().stream()
                        .map(
                                name ->
                                        SemanticUtils.normalizeObjectName(
                                                name,
                                                "Missing tableName for selected table semantic"
                                                        + " sync."))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        List<TableSyncSource> presentTables = new ArrayList<>();
        List<String> missingTableNames = new ArrayList<>();
        for (String normalizedTableName : selectedTableNames) {
            PhysicalTableInfo physicalTable = physicalTables.get(normalizedTableName);
            if (physicalTable == null) {
                missingTableNames.add(normalizedTableName);
                continue;
            }
            presentTables.add(readTableSyncSource(datasource, physicalTable, normalizedTableName));
        }

        List<SyncTableResult> results =
                transactionTemplate.execute(
                        status ->
                                semanticSyncApplyService.applyTableSync(
                                        request.datasourceId(), presentTables, missingTableNames));
        return semanticSyncResultService.summarize(results == null ? List.of() : results);
    }

    @Override
    public SyncTableSemanticsResponse refreshPhysicalStatus(RefreshPhysicalStatusRequest request) {
        Datasource datasource = requireDatasource(request.datasourceId());
        Map<String, PhysicalTableInfo> physicalTables =
                loadPhysicalTableIndex(
                        datasource,
                        "Missing physical tableName while loading tables for physical status"
                                + " refresh.");
        Map<String, Set<String>> physicalColumnNamesByTable =
                loadPhysicalColumnNamesByTable(datasource, physicalTables);
        PhysicalStatusRefreshPlan refreshPlan =
                semanticSyncApplyService.buildPhysicalStatusRefreshPlan(
                        request.datasourceId(), physicalTables, physicalColumnNamesByTable);
        List<SyncTableResult> results =
                transactionTemplate.execute(
                        status -> semanticSyncApplyService.applyPhysicalStatusRefresh(refreshPlan));
        return semanticSyncResultService.summarize(results == null ? List.of() : results);
    }

    private Map<String, PhysicalTableInfo> loadPhysicalTableIndex(
            Datasource datasource, String missingMessage) {
        // External schema reads stay outside the write transaction; only the prepared diff is
        // applied transactionally.
        return schemaReader.getTables(datasource).stream()
                .collect(
                        Collectors.toMap(
                                table ->
                                        SemanticUtils.normalizeObjectName(
                                                table.tableName(), missingMessage),
                                table -> table,
                                (left, _right) -> left,
                                LinkedHashMap::new));
    }

    private Set<String> loadSyncedTableNames(
            Integer datasourceId, List<PhysicalTableInfo> pageTables) {
        // Candidate pages only need sync status for the current page, not every semantic table.
        if (pageTables.isEmpty()) {
            return Set.of();
        }
        List<String> tableNames =
                pageTables.stream().map(PhysicalTableInfo::tableName).distinct().toList();
        return tableInfoMapper.selectByDatasourceIdAndTableNames(datasourceId, tableNames).stream()
                .map(
                        table ->
                                SemanticUtils.normalizeObjectName(
                                        table.getTableName(),
                                        "Missing tableName while loading synced table"
                                                + " candidates."))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private TableSyncSource readTableSyncSource(
            Datasource datasource, PhysicalTableInfo physicalTable, String normalizedTableName) {
        // Snapshot physical columns before opening the transaction so JDBC metadata latency does
        // not extend row lock time.
        List<ColumnSyncSource> columns =
                schemaReader.getTableSchema(datasource, physicalTable.tableName()).stream()
                        .map(column -> toColumnSyncSource(normalizedTableName, column))
                        .toList();
        return new TableSyncSource(
                normalizedTableName,
                physicalTable.tableName(),
                SemanticUtils.trimToNull(physicalTable.remarks()),
                columns);
    }

    private ColumnSyncSource toColumnSyncSource(
            String normalizedTableName, PhysicalColumnInfo physicalColumn) {
        return new ColumnSyncSource(
                normalizedTableName,
                SemanticUtils.normalizeObjectName(
                        physicalColumn.columnName(),
                        "Missing physical columnName while syncing table."),
                SemanticUtils.trimToNull(physicalColumn.remarks()),
                SemanticUtils.trimToNull(physicalColumn.typeName()),
                physicalColumn.primaryKey());
    }

    private Map<String, Set<String>> loadPhysicalColumnNamesByTable(
            Datasource datasource, Map<String, PhysicalTableInfo> physicalTables) {
        // Refresh compares semantic cache against the current physical schema first, then applies
        // missing-status updates in one short transaction.
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
}

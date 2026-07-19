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

@Service
@RequiredArgsConstructor
public class SemanticSyncServiceImpl implements SemanticSyncService {

    private final DatasourceService datasourceService;
    private final TableInfoMapper tableInfoMapper;
    private final SchemaReader schemaReader;
    private final SemanticSyncApplyService semanticSyncApplyService;
    private final SemanticSyncResultService semanticSyncResultService;

    @Override
    public PageResponse<PhysicalTableCandidateResponse> getPhysicalTableCandidates(
            PhysicalTableCandidatePageQuery query) {
        Datasource datasource = requireDatasource(query.datasourceId());
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        String keyword = SemanticUtils.trimToNull(query.keyword());
        Comparator<PhysicalTableInfo> comparator =
                Comparator.comparing(
                        PhysicalTableInfo::tableName, String.CASE_INSENSITIVE_ORDER);
        if (SemanticUtils.isDescendingSort(query.sortOrder())) {
            comparator = comparator.reversed();
        }
        // SchemaReader 一次性读取物理表，筛选、排序和分页统一在内存中完成。
        List<PhysicalTableInfo> filteredTables =
                schemaReader.getTables(datasource).stream()
                        .filter(
                                table ->
                                        keyword == null
                                                || SemanticUtils.containsIgnoreCase(
                                                        table.tableName(), keyword)
                                                || SemanticUtils.containsIgnoreCase(
                                                        table.remarks(), keyword))
                        .sorted(comparator)
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
                                                                "Missing physical tableName."))))
                        .toList();
        return PageResponse.of(responses, filteredTables.size(), pageNumber, pageSize);
    }

    @Override
    public SyncTableSemanticsResponse syncTables(SyncTableSemanticsRequest request) {
        Datasource datasource = requireDatasource(request.datasourceId());
        Map<String, PhysicalTableInfo> physicalTables = loadPhysicalTableIndex(datasource);
        Set<String> selectedTableNames = new LinkedHashSet<>();
        for (String tableName : request.tableNames()) {
            selectedTableNames.add(
                    SemanticUtils.normalizeObjectName(
                            tableName, "Missing physical tableName."));
        }

        List<TableSyncSource> presentTables = new ArrayList<>();
        List<String> missingTableNames = new ArrayList<>();
        // 先读取并整理全部物理结构，再交给事务方法批量写库，避免事务中访问外部数据源。
        for (String normalizedTableName : selectedTableNames) {
            PhysicalTableInfo physicalTable = physicalTables.get(normalizedTableName);
            if (physicalTable == null) {
                missingTableNames.add(normalizedTableName);
                continue;
            }
            presentTables.add(readTableSyncSource(datasource, physicalTable, normalizedTableName));
        }

        List<SyncTableResult> results =
                semanticSyncApplyService.applyTableSync(
                        request.datasourceId(), presentTables, missingTableNames);
        return semanticSyncResultService.summarize(results);
    }

    @Override
    public SyncTableSemanticsResponse refreshPhysicalStatus(RefreshPhysicalStatusRequest request) {
        Datasource datasource = requireDatasource(request.datasourceId());
        Map<String, PhysicalTableInfo> physicalTables = loadPhysicalTableIndex(datasource);
        Map<String, Set<String>> physicalColumnNamesByTable =
                loadPhysicalColumnNamesByTable(datasource, physicalTables);
        List<SyncTableResult> results =
                semanticSyncApplyService.refreshPhysicalStatus(
                        request.datasourceId(), physicalTables.keySet(), physicalColumnNamesByTable);
        return semanticSyncResultService.summarize(results);
    }

    private Map<String, PhysicalTableInfo> loadPhysicalTableIndex(Datasource datasource) {
        Map<String, PhysicalTableInfo> result = new LinkedHashMap<>();
        for (PhysicalTableInfo table : schemaReader.getTables(datasource)) {
            String tableName =
                    SemanticUtils.normalizeObjectName(
                            table.tableName(), "Missing physical tableName.");
            result.putIfAbsent(tableName, table);
        }
        return result;
    }

    private Set<String> loadSyncedTableNames(
            Integer datasourceId, List<PhysicalTableInfo> pageTables) {
        // 只查询当前页表的同步状态，避免加载数据源下全部语义表。
        if (pageTables.isEmpty()) {
            return Set.of();
        }
        List<String> tableNames =
                pageTables.stream().map(PhysicalTableInfo::tableName).distinct().toList();
        return tableInfoMapper.selectByDatasourceIdAndTableNames(datasourceId, tableNames).stream()
                .map(
                        table ->
                                SemanticUtils.normalizeObjectName(
                                        table.getTableName(), "Missing physical tableName."))
                .collect(Collectors.toSet());
    }

    private TableSyncSource readTableSyncSource(
            Datasource datasource, PhysicalTableInfo physicalTable, String normalizedTableName) {
        // 在开启写事务前读取物理列快照，避免 JDBC 元数据读取延长事务时间。
        List<ColumnSyncSource> columns = new ArrayList<>();
        for (PhysicalColumnInfo column :
                schemaReader.getTableSchema(datasource, physicalTable.tableName())) {
            columns.add(
                    new ColumnSyncSource(
                            SemanticUtils.normalizeObjectName(
                                    column.columnName(), "Missing physical columnName."),
                            SemanticUtils.trimToNull(column.remarks()),
                            SemanticUtils.trimToNull(column.typeName()),
                            column.primaryKey()));
        }
        return new TableSyncSource(
                normalizedTableName,
                physicalTable.tableName(),
                SemanticUtils.trimToNull(physicalTable.remarks()),
                columns);
    }

    private Map<String, Set<String>> loadPhysicalColumnNamesByTable(
            Datasource datasource, Map<String, PhysicalTableInfo> physicalTables) {
        List<String> tableNames =
                physicalTables.values().stream().map(PhysicalTableInfo::tableName).toList();
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry :
                schemaReader.getTableColumnNames(datasource, tableNames).entrySet()) {
            String tableName =
                    SemanticUtils.normalizeObjectName(
                            entry.getKey(), "Missing physical tableName.");
            Set<String> columnNames =
                    result.computeIfAbsent(tableName, _key -> new LinkedHashSet<>());
            for (String columnName : entry.getValue()) {
                columnNames.add(
                        SemanticUtils.normalizeObjectName(
                                columnName, "Missing physical columnName."));
            }
        }
        return result;
    }

    private Datasource requireDatasource(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
        return datasource;
    }
}

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
import io.github.malonetalk.dto.datasource.PhysicalTableInfo;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.PhysicalTableCandidatePageQuery;
import io.github.malonetalk.dto.semantic.PhysicalTableCandidateResponse;
import io.github.malonetalk.dto.semantic.RefreshPhysicalStatusRequest;
import io.github.malonetalk.dto.semantic.SyncTableResult;
import io.github.malonetalk.dto.semantic.SyncTableSemanticsRequest;
import io.github.malonetalk.dto.semantic.SyncTableSemanticsResponse;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.bo.TableSyncSourceBo;
import io.github.malonetalk.model.converter.SemanticSyncConverter;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.DatasourceWriteLockService;
import io.github.malonetalk.service.csv.CsvAccessService;
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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SemanticSyncServiceImpl implements SemanticSyncService {

    private final DatasourceService datasourceService;
    private final DatasourceWriteLockService datasourceWriteLock;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final SchemaReader schemaReader;
    private final SemanticSyncApplyService semanticSyncApplyService;
    private final SemanticSyncResultService semanticSyncResultService;
    private final CsvAccessService csvAccess;
    private final SemanticSyncConverter converter;

    @Override
    public PageResponse<PhysicalTableCandidateResponse> getPhysicalTableCandidates(
            PhysicalTableCandidatePageQuery query) {
        DatasourcePo datasource = requireDatasource(query.datasourceId());
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        String keyword = SemanticUtils.trimToNull(query.keyword());
        Comparator<PhysicalTableInfo> comparator =
                Comparator.comparing(PhysicalTableInfo::tableName, String.CASE_INSENSITIVE_ORDER);
        if (SemanticUtils.isDescendingSort(query.sortOrder())) {
            comparator = comparator.reversed();
        }
        List<TableInfo> semanticTables = tableInfoMapper.selectByDatasourceId(datasource.getId());
        Set<String> syncedTableNames =
                semanticTables.stream()
                        .map(
                                table ->
                                        SemanticUtils.normalizeObjectName(
                                                table.getTableName(),
                                                "Missing semantic tableName."))
                        .collect(Collectors.toSet());
        // SchemaReader 一次性读取表和普通视图，筛选、排序和分页统一在内存中完成。
        List<PhysicalTableInfo> filteredTables =
                loadCandidateTables(datasource, semanticTables).stream()
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
        List<PhysicalTableCandidateResponse> responses =
                pageTables.stream()
                        .map(
                                table ->
                                        new PhysicalTableCandidateResponse(
                                                table.tableName(),
                                                SemanticUtils.trimToNull(table.remarks()),
                                                table.objectType(),
                                                syncedTableNames.contains(
                                                        SemanticUtils.normalizeObjectName(
                                                                table.tableName(),
                                                                "Missing physical tableName."))))
                        .toList();
        return PageResponse.of(responses, filteredTables.size(), pageNumber, pageSize);
    }

    private List<PhysicalTableInfo> loadCandidateTables(
            DatasourcePo datasource, List<TableInfo> semanticTables) {
        Map<String, PhysicalTableInfo> candidates = loadPhysicalTableIndex(datasource);
        var ready = csvAccess.readyFilter(datasource);
        // 保留语义缓存中的对象，供用户发现已从数据源消失的表或视图。
        for (TableInfo table : semanticTables) {
            if (!ready.test(table.getTableName())) continue;
            String tableName =
                    SemanticUtils.requireTrimmed(
                            table.getTableName(), "Missing semantic tableName.");
            candidates.putIfAbsent(
                    SemanticUtils.normalizeObjectName(tableName, "Missing semantic tableName."),
                    new PhysicalTableInfo(
                            tableName,
                            SemanticUtils.trimToNull(table.getPhysicalTableDescription()),
                            table.getObjectType()));
        }
        return List.copyOf(candidates.values());
    }

    @Override
    @Transactional
    public SyncTableSemanticsResponse syncTables(SyncTableSemanticsRequest request) {
        SemanticUtils.requireDatasourceId(request.datasourceId());
        DatasourcePo datasource = datasourceWriteLock.lock(request.datasourceId());
        Map<String, PhysicalTableInfo> physicalTables = loadPhysicalTableIndex(datasource);
        Map<String, String> selectedTableNames = new LinkedHashMap<>();
        for (String tableName : request.tableNames()) {
            String trimmedTableName =
                    SemanticUtils.requireTrimmed(tableName, "Missing physical tableName.");
            csvAccess.requireReady(datasource, trimmedTableName);
            selectedTableNames.putIfAbsent(
                    SemanticUtils.normalizeObjectName(
                            trimmedTableName, "Missing physical tableName."),
                    trimmedTableName);
        }

        List<TableSyncSourceBo> presentTables = new ArrayList<>();
        List<String> missingTableNames = new ArrayList<>();
        // Keep publication/deletion out of the interval between reading and applying the snapshot.
        for (Map.Entry<String, String> selectedTable : selectedTableNames.entrySet()) {
            PhysicalTableInfo physicalTable = physicalTables.get(selectedTable.getKey());
            if (physicalTable == null) {
                missingTableNames.add(selectedTable.getValue());
                continue;
            }
            presentTables.add(readTableSyncSource(datasource, physicalTable));
        }

        List<SyncTableResult> results =
                semanticSyncApplyService.applyTableSync(
                        request.datasourceId(), presentTables, missingTableNames);
        return semanticSyncResultService.summarize(results);
    }

    @Override
    @Transactional
    public SyncTableSemanticsResponse refreshPhysicalStatus(RefreshPhysicalStatusRequest request) {
        SemanticUtils.requireDatasourceId(request.datasourceId());
        // Lock before any ordinary reads, so a waiting transaction sees the latest CSV READY state.
        DatasourcePo datasource = datasourceWriteLock.lock(request.datasourceId());
        List<TableInfo> semanticTables =
                tableInfoMapper.selectByDatasourceIdForUpdate(datasource.getId());
        if (semanticTables.isEmpty()) return semanticSyncResultService.summarize(List.of());
        List<ColumnInfo> semanticColumns =
                columnSemanticInfoMapper.selectByDatasourceIdForUpdate(datasource.getId());
        // Hold these rows through the physical read; update_time has only second precision.
        Map<String, PhysicalTableInfo> physicalTables = loadPhysicalTableIndex(datasource);
        Map<String, Set<String>> physicalColumnNamesByTable =
                loadPhysicalColumnNamesByTable(datasource, physicalTables);
        List<SyncTableResult> results =
                semanticSyncApplyService.refreshPhysicalStatus(
                        semanticTables,
                        semanticColumns,
                        physicalTables.keySet(),
                        physicalColumnNamesByTable);
        return semanticSyncResultService.summarize(results);
    }

    private Map<String, PhysicalTableInfo> loadPhysicalTableIndex(DatasourcePo datasource) {
        Map<String, PhysicalTableInfo> result = new LinkedHashMap<>();
        for (PhysicalTableInfo table : schemaReader.getTables(datasource)) {
            String tableName =
                    SemanticUtils.normalizeObjectName(
                            table.tableName(), "Missing physical tableName.");
            result.putIfAbsent(tableName, table);
        }
        return result;
    }

    private TableSyncSourceBo readTableSyncSource(
            DatasourcePo datasource, PhysicalTableInfo physicalTable) {
        return converter.toTable(
                physicalTable.tableName(),
                physicalTable.remarks(),
                physicalTable.objectType(),
                schemaReader.getTableSchema(datasource, physicalTable));
    }

    private Map<String, Set<String>> loadPhysicalColumnNamesByTable(
            DatasourcePo datasource, Map<String, PhysicalTableInfo> physicalTables) {
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

    private DatasourcePo requireDatasource(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        return datasourceService
                .findById(datasourceId)
                .orElseThrow(
                        () ->
                                BusinessException.of(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Datasource does not exist: " + datasourceId));
    }
}

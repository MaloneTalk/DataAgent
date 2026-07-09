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
import java.util.Locale;
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
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final SchemaReader schemaReader;
    private final SemanticSyncDiffService semanticSyncDiffService;
    private final SemanticSyncResultService semanticSyncResultService;

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
                                                        table.getTableName()),
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
                                                                table.tableName()))))
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
                                                        table.tableName()),
                                        table -> table,
                                        (left, _right) -> left,
                                        LinkedHashMap::new));
        Set<String> selectedTableNames =
                request.tableNames().stream()
                        .map(name -> SemanticUtils.trimToNotBlank(name, "tableName"))
                        .map(SemanticUtils::normalizeObjectName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        List<SyncTableResult> results = new ArrayList<>();
        for (String normalizedTableName : selectedTableNames) {
            PhysicalTableInfo physicalTable = physicalTables.get(normalizedTableName);
            SyncTableResult result =
                    physicalTable == null
                            ? semanticSyncDiffService.markMissingTable(
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

    private SyncTableResult syncSingleTable(
            Datasource datasource,
            Integer datasourceId,
            PhysicalTableInfo physicalTable,
            String normalizedTableName) {
        LocalDateTime now = LocalDateTime.now();
        SemanticSyncDiffService.TableSyncDiff tableSyncDiff =
                semanticSyncDiffService.ensureTablePresent(
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
                                                        column.getColumnName()),
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
                    SemanticUtils.normalizeObjectName(physicalColumn.columnName());
            physicalColumnNames.add(normalizedColumnName);
            SemanticSyncDiffService.ColumnSyncDiff columnSyncDiff =
                    semanticSyncDiffService.ensureColumnPresent(
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
                semanticSyncDiffService.markMissingColumns(
                        semanticColumns, physicalColumnNames, now);

        return new SyncTableResult(
                physicalTable.tableName(),
                true,
                tableSyncDiff.added(),
                tableSyncDiff.reactivated(),
                tableSyncDiff.updated(),
                false,
                addedColumns,
                reactivatedColumns,
                updatedColumns,
                missingColumnsMarked,
                "同步完成");
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
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return table.tableName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                || (table.remarks() != null
                        && table.remarks().toLowerCase(Locale.ROOT).contains(normalizedKeyword));
    }

    private Comparator<PhysicalTableInfo> buildTableComparator(boolean sortDescending) {
        Comparator<PhysicalTableInfo> comparator =
                Comparator.comparing(
                        table -> table.tableName().toLowerCase(Locale.ROOT),
                        Comparator.naturalOrder());
        return sortDescending ? comparator.reversed() : comparator;
    }
}

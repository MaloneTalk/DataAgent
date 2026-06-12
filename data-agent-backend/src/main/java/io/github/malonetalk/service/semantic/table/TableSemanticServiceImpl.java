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
package io.github.malonetalk.service.semantic.table;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.github.malonetalk.agent.tools.response.TablePromptResponse;
import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.TableSemanticPageQuery;
import io.github.malonetalk.dto.semantic.TableSemanticResponse;
import io.github.malonetalk.dto.semantic.TableSemanticUpdateRequest;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.SemanticMergeService;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TableSemanticServiceImpl implements TableSemanticService {

    private final DatasourceService datasourceService;
    private final TableInfoMapper tableInfoMapper;
    private final SemanticMergeService semanticMergeService;

    @Override
    public PageResponse<TableSemanticResponse> getTablePage(TableSemanticPageQuery query) {
        SemanticUtils.requireDatasourceId(query.datasourceId());
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        Datasource datasource = datasourceService.findById(query.datasourceId());
        if (datasource == null) {
            return PageResponse.empty(pageNumber, pageSize);
        }
        SemanticUtils.validateSortOrder(query.sortOrder());
        boolean sortDescending =
                SemanticConstants.SORT_ORDER_DESC.equalsIgnoreCase(query.sortOrder());
        PageHelper.startPage(pageNumber, pageSize);
        Page<TableInfo> page =
                (Page<TableInfo>)
                        tableInfoMapper.selectPageByDatasourceId(
                                new TableSemanticPageQuery(
                                        query.datasourceId(),
                                        pageNumber,
                                        pageSize,
                                        SemanticUtils.normalizeBlankToNull(query.keyword()),
                                        query.sortOrder()),
                                sortDescending);
        List<TableSemanticResponse> responses = page.stream().map(this::mapResponse).toList();
        long total = page.getTotal();
        return PageResponse.of(responses, total, pageNumber, pageSize);
    }

    @Override
    public List<String> listTableNames(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            return List.of();
        }
        return tableInfoMapper.selectByDatasourceId(datasourceId).stream()
                .map(TableInfo::getTableName)
                .filter(tableName -> tableName != null && !tableName.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    @Override
    public List<String> listAvailableDomains(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            return List.of();
        }
        return tableInfoMapper.selectByDatasourceId(datasourceId).stream()
                .map(TableInfo::getDomain)
                .filter(domain -> domain != null && !domain.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    @Override
    public List<TableInfo> listTableInfosByDatasourceId(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            return List.of();
        }
        return tableInfoMapper.selectByDatasourceId(datasourceId);
    }

    @Override
    public List<TableInfo> listTableInfosByDomains(Integer datasourceId, List<String> domains) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            return List.of();
        }
        if (domains == null || domains.isEmpty()) {
            return listTableInfosByDatasourceId(datasourceId);
        }
        return tableInfoMapper.selectByDatasourceIdAndDomains(datasourceId, domains);
    }

    @Override
    public List<TablePromptResponse> listMergedTablesByDomains(
            Integer datasourceId, List<String> domains) {
        SemanticUtils.requireDatasourceId(datasourceId);
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return List.of();
        }
        return semanticMergeService.listVisibleTablesByDomains(datasource, domains);
    }

    private String normalizeName(String value) {
        return SemanticUtils.requireName(value, "tableName").toLowerCase(Locale.ROOT);
    }

    private String normalizeDomain(String value) {
        String normalized = SemanticUtils.normalizeBlankToNull(value);
        return normalized == null ? SemanticConstants.DEFAULT_DOMAIN : normalized;
    }

    @Override
    public void updateTableSemantic(TableSemanticUpdateRequest request) {
        requireDatasource(request.datasourceId());
        String normalizedTableName = SemanticUtils.requireName(request.tableName(), "tableName");
        TableInfo existing =
                tableInfoMapper.selectByDatasourceIdAndTableName(
                        request.datasourceId(), normalizedTableName);
        if (existing == null) {
            TableInfo tableInfo = new TableInfo();
            tableInfo.setDatasourceId(request.datasourceId());
            tableInfo.setTableName(normalizedTableName);
            tableInfo.setTableDescription(
                    SemanticUtils.normalizeBlankToNull(request.tableDescription()));
            tableInfo.setDomain(normalizeDomain(request.domain()));
            tableInfo.setIsVisible(request.isVisible());
            tableInfo.setCreateTime(LocalDateTime.now());
            tableInfo.setUpdateTime(LocalDateTime.now());
            tableInfoMapper.insert(tableInfo);
            return;
        }
        existing.setTableName(normalizedTableName);
        existing.setTableDescription(
                SemanticUtils.normalizeBlankToNull(request.tableDescription()));
        existing.setDomain(normalizeDomain(request.domain()));
        existing.setIsVisible(request.isVisible());
        existing.setUpdateTime(LocalDateTime.now());
        tableInfoMapper.update(existing);
    }

    @Override
    public void resetTableSemantic(Integer datasourceId, String tableName) {
        requireDatasource(datasourceId);
        String normalizedTableName = SemanticUtils.requireName(tableName, "tableName");
        TableInfo existing =
                tableInfoMapper.selectByDatasourceIdAndTableName(datasourceId, normalizedTableName);
        if (existing == null) {
            throw new IllegalArgumentException("Table semantic metadata does not exist.");
        }
        tableInfoMapper.deleteByDatasourceIdAndIds(datasourceId, List.of(existing.getId()));
    }

    @Override
    public int resetTableSemantics(Integer datasourceId, List<String> tableNames) {
        requireDatasource(datasourceId);
        if (tableNames == null || tableNames.isEmpty()) {
            return 0;
        }
        Set<String> normalizedNames =
                tableNames.stream()
                        .map(this::normalizeName)
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<Integer> matchedIds =
                tableInfoMapper.selectByDatasourceId(datasourceId).stream()
                        .filter(
                                table ->
                                        normalizedNames.contains(
                                                normalizeName(table.getTableName())))
                        .map(TableInfo::getId)
                        .distinct()
                        .toList();
        if (matchedIds.isEmpty()) {
            return 0;
        }
        if (matchedIds.size() != normalizedNames.size()) {
            throw new IllegalArgumentException(
                    "Some table semantic metadata does not exist for datasource "
                            + datasourceId
                            + ".");
        }
        return tableInfoMapper.deleteByDatasourceIdAndIds(datasourceId, matchedIds);
    }

    private void requireDatasource(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
    }

    private List<TableSemanticResponse> listMergedTableResponses(Datasource datasource) {
        Map<String, TableInfo> semanticByKey = new LinkedHashMap<>();
        for (TableInfo tableInfo : tableInfoMapper.selectByDatasourceId(datasource.getId())) {
            semanticByKey.put(normalizeName(tableInfo.getTableName()), tableInfo);
        }

        Map<String, TableSemanticResponse> responsesByKey = new LinkedHashMap<>();
        for (io.github.malonetalk.agent.datasource.TableInfo physicalTable :
                schemaReader.getTables(datasource)) {
            String key = normalizeName(physicalTable.tableName());
            TableInfo semanticTable = semanticByKey.remove(key);
            responsesByKey.put(key, mapTableResponse(physicalTable, semanticTable, true));
        }
        for (TableInfo semanticTable : semanticByKey.values()) {
            responsesByKey.put(
                    normalizeName(semanticTable.getTableName()),
                    mapTableResponse(null, semanticTable, false));
        }
        return List.copyOf(responsesByKey.values());
    }

    private TableSemanticResponse mapTableResponse(
            io.github.malonetalk.agent.datasource.TableInfo physicalTable,
            TableInfo semanticTable,
            boolean hasPhysicalTable) {
        String tableName =
                physicalTable == null ? semanticTable.getTableName() : physicalTable.tableName();
        Boolean isVisible = semanticTable == null ? Boolean.TRUE : semanticTable.getIsVisible();
        boolean visible = Boolean.TRUE.equals(isVisible);
        String physicalDescription =
                physicalTable == null
                        ? null
                        : SemanticUtils.normalizeBlankToNull(physicalTable.remarks());
        return new TableSemanticResponse(
                semanticTable == null ? null : semanticTable.getId(),
                tableName,
                semanticTable == null
                        ? SemanticConstants.DEFAULT_DOMAIN
                        : normalizeDomain(semanticTable.getDomain()),
                physicalDescription,
                semanticTable == null
                        ? null
                        : SemanticUtils.normalizeBlankToNull(semanticTable.getTableDescription()),
                isVisible,
                hasPhysicalTable,
                hasPhysicalTable && visible,
                hasPhysicalTable ? null : "物理表不存在",
                semanticTable == null ? null : semanticTable.getUpdateTime());
    }

    private boolean tableMatchesKeyword(TableSemanticResponse table, String keyword) {
        if (keyword == null) {
            return true;
        }
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(table.tableName(), normalizedKeyword)
                || containsIgnoreCase(table.domain(), normalizedKeyword)
                || containsIgnoreCase(table.tableDescription(), normalizedKeyword)
                || containsIgnoreCase(table.physicalTableDescription(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private Comparator<TableSemanticResponse> tableComparator(boolean sortDescending) {
        Comparator<TableSemanticResponse> comparator =
                Comparator.comparing(
                        table -> table.tableName().toLowerCase(Locale.ROOT),
                        Comparator.naturalOrder());
        return sortDescending ? comparator.reversed() : comparator;
    }

    private List<TableSemanticResponse> pageItems(
            List<TableSemanticResponse> responses, int pageNumber, int pageSize) {
        int fromIndex = Math.min((pageNumber - 1) * pageSize, responses.size());
        int toIndex = Math.min(fromIndex + pageSize, responses.size());
        return responses.subList(fromIndex, toIndex);
    }
}

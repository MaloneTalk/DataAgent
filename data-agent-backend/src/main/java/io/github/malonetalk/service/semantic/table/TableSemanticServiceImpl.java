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

import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.TableSemanticResponse;
import io.github.malonetalk.dto.semantic.TableSemanticUpdateRequest;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TableSemanticServiceImpl implements TableSemanticService {

    private final DatasourceService datasourceService;
    private final TableInfoMapper tableInfoMapper;

    public TableSemanticServiceImpl(
            DatasourceService datasourceService, TableInfoMapper tableInfoMapper) {
        this.datasourceService = datasourceService;
        this.tableInfoMapper = tableInfoMapper;
    }

    @Override
    public PageResponse<TableSemanticResponse> getTablePage(
            Integer datasourceId, PageRequest pageRequest, String keywordPrefix, String sortOrder) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            return PageResponse.empty(pageRequest);
        }
        SemanticUtils.validateSortOrder(sortOrder);
        List<TableSemanticResponse> responses =
                tableInfoMapper.selectByDatasourceId(datasourceId).stream()
                        .filter(
                                table ->
                                        SemanticUtils.matchesKeywordPrefix(
                                                table.getTableName(), keywordPrefix))
                        .sorted(buildTableComparator(sortOrder))
                        .map(this::mapResponse)
                        .toList();
        return PageResponse.from(responses, pageRequest);
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
    public void updateTableSemantic(TableSemanticUpdateRequest request) {
        requireDatasource(request.datasourceId());
        TableInfo existing =
                tableInfoMapper.selectByDatasourceIdAndTableName(
                        request.datasourceId(), request.tableName());
        if (existing == null) {
            TableInfo tableInfo = new TableInfo();
            tableInfo.setDatasourceId(request.datasourceId());
            tableInfo.setTableName(request.tableName().trim());
            tableInfo.setTableDescription(
                    SemanticUtils.normalizeBlankToNull(request.tableDescription()));
            tableInfo.setDomain(SemanticUtils.normalizeBlankToNull(request.domain()));
            tableInfo.setIsVisible(request.isVisible());
            tableInfo.setCreateTime(LocalDateTime.now());
            tableInfo.setUpdateTime(LocalDateTime.now());
            tableInfoMapper.insert(tableInfo);
            return;
        }
        existing.setTableName(request.tableName().trim());
        existing.setTableDescription(
                SemanticUtils.normalizeBlankToNull(request.tableDescription()));
        existing.setDomain(SemanticUtils.normalizeBlankToNull(request.domain()));
        existing.setIsVisible(request.isVisible());
        existing.setUpdateTime(LocalDateTime.now());
        tableInfoMapper.update(existing);
    }

    @Override
    public void resetTableSemantic(Integer datasourceId, String tableName) {
        requireDatasource(datasourceId);
        TableInfo existing = tableInfoMapper.selectByDatasourceIdAndTableName(datasourceId, tableName);
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
                    "Some table semantic metadata does not exist for datasource " + datasourceId + ".");
        }
        return tableInfoMapper.deleteByDatasourceIdAndIds(datasourceId, matchedIds);
    }

    private void requireDatasource(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
    }

    private Comparator<TableInfo> buildTableComparator(String sortOrder) {
        Comparator<TableInfo> comparator =
                Comparator.comparing(
                                TableInfo::getTableName,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(TableInfo::getId);
        if (SemanticConstants.SORT_ORDER_DESC.equalsIgnoreCase(sortOrder)) {
            return comparator.reversed();
        }
        return comparator;
    }

    private TableSemanticResponse mapResponse(TableInfo tableInfo) {
        return new TableSemanticResponse(
                tableInfo.getId(),
                tableInfo.getTableName(),
                tableInfo.getDomain(),
                null,
                tableInfo.getTableDescription(),
                tableInfo.getIsVisible(),
                true,
                Boolean.TRUE.equals(tableInfo.getIsVisible()),
                null,
                tableInfo.getUpdateTime());
    }

    private String normalizeName(String value) {
        return SemanticUtils.requireName(value, "tableName").toLowerCase(Locale.ROOT);
    }
}

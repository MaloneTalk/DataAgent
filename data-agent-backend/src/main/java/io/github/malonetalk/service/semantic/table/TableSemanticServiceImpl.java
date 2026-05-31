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

    @Override
    public PageResponse<TableSemanticResponse> getTablePage(
            Integer datasourceId, PageRequest pageRequest, String keywordPrefix, String sortOrder) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            return PageResponse.empty(pageRequest);
        }
        SemanticUtils.validateSortOrder(sortOrder);
        boolean sortDescending = SemanticConstants.SORT_ORDER_DESC.equalsIgnoreCase(sortOrder);
        String normalizedPrefix = SemanticUtils.normalizeBlankToNull(keywordPrefix);
        PageHelper.startPage(pageRequest.page(), pageRequest.pageSize());
        Page<TableInfo> page =
                (Page<TableInfo>)
                        tableInfoMapper.selectPageByDatasourceId(
                                datasourceId, normalizedPrefix, sortDescending);
        List<TableSemanticResponse> responses = page.stream().map(this::mapResponse).toList();
        long total = page.getTotal();
        return PageResponse.of(responses, total, pageRequest);
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
            tableInfo.setDomain(SemanticUtils.normalizeBlankToNull(request.domain()));
            tableInfo.setIsVisible(request.isVisible());
            tableInfo.setCreateTime(LocalDateTime.now());
            tableInfo.setUpdateTime(LocalDateTime.now());
            tableInfoMapper.insert(tableInfo);
            return;
        }
        existing.setTableName(normalizedTableName);
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

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
package io.github.malonetalk.service.semantic.column;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticPageQuery;
import io.github.malonetalk.dto.semantic.ColumnSemanticResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticUpdateRequest;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
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
public class ColumnSemanticServiceImpl implements ColumnSemanticService {

    private final DatasourceService datasourceService;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;

    @Override
    public PageResponse<ColumnSemanticResponse> getColumnPage(ColumnSemanticPageQuery query) {
        SemanticUtils.requireDatasourceId(query.datasourceId());
        String normalizedTableName = SemanticUtils.requireName(query.tableName(), "tableName");
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        if (datasourceService.findById(query.datasourceId()) == null) {
            return PageResponse.empty(pageNumber, pageSize);
        }
        SemanticUtils.validateSortOrder(query.sortOrder());
        boolean sortDescending =
                SemanticConstants.SORT_ORDER_DESC.equalsIgnoreCase(query.sortOrder());
        PageHelper.startPage(pageNumber, pageSize);
        Page<ColumnInfo> page =
                (Page<ColumnInfo>)
                        columnSemanticInfoMapper.selectPageByDatasourceIdAndTableName(
                                new ColumnSemanticPageQuery(
                                        query.datasourceId(),
                                        normalizedTableName,
                                        pageNumber,
                                        pageSize,
                                        SemanticUtils.normalizeBlankToNull(query.keyword()),
                                        query.sortOrder()),
                                sortDescending);
        List<ColumnSemanticResponse> responses = page.stream().map(this::mapResponse).toList();
        return PageResponse.of(responses, page.getTotal(), pageNumber, pageSize);
    }

    @Override
    public void updateColumnSemantic(
            String tableName, ColumnSemanticUpdateRequest request) {
        requireDatasource(request.datasourceId());
        String normalizedTableName = SemanticUtils.requireName(tableName, "tableName");
        String normalizedColumnName = SemanticUtils.requireName(request.columnName(), "columnName");
        ColumnInfo existing =
                columnSemanticInfoMapper.selectByDatasourceIdAndTableNameAndColumnName(
                        request.datasourceId(), normalizedTableName, normalizedColumnName);
        if (existing == null) {
            ColumnInfo columnInfo = new ColumnInfo();
            columnInfo.setDatasourceId(request.datasourceId());
            columnInfo.setTableName(normalizedTableName);
            columnInfo.setColumnName(normalizedColumnName);
            columnInfo.setColumnDescription(
                    SemanticUtils.normalizeBlankToNull(request.columnDescription()));
            columnInfo.setIsVisible(request.isVisible());
            columnInfo.setCreateTime(LocalDateTime.now());
            columnInfo.setUpdateTime(LocalDateTime.now());
            columnSemanticInfoMapper.insert(columnInfo);
            return;
        }
        existing.setTableName(normalizedTableName);
        existing.setColumnName(normalizedColumnName);
        existing.setColumnDescription(
                SemanticUtils.normalizeBlankToNull(request.columnDescription()));
        existing.setIsVisible(request.isVisible());
        existing.setUpdateTime(LocalDateTime.now());
        columnSemanticInfoMapper.update(existing);
    }

    @Override
    public void resetColumnSemantic(Integer datasourceId, String tableName, String columnName) {
        requireDatasource(datasourceId);
        ColumnInfo existing =
                columnSemanticInfoMapper.selectByDatasourceIdAndTableNameAndColumnName(
                        datasourceId, tableName, columnName);
        if (existing == null) {
            throw new IllegalArgumentException("Column semantic metadata does not exist.");
        }
        columnSemanticInfoMapper.deleteByDatasourceIdAndIds(
                datasourceId, List.of(existing.getId()));
    }

    @Override
    public int resetColumnSemantics(
            Integer datasourceId, String tableName, List<String> columnNames) {
        requireDatasource(datasourceId);
        String normalizedTableName = SemanticUtils.requireName(tableName, "tableName");
        if (columnNames == null || columnNames.isEmpty()) {
            return 0;
        }
        Set<String> normalizedColumnNames =
                columnNames.stream()
                        .map(
                                columnName ->
                                        normalizeKey(
                                                SemanticUtils.requireName(
                                                        columnName, "columnName")))
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<Integer> matchedIds =
                columnSemanticInfoMapper
                        .selectByDatasourceIdAndTableName(datasourceId, normalizedTableName)
                        .stream()
                        .filter(
                                column ->
                                        normalizedColumnNames.contains(
                                                normalizeKey(column.getColumnName())))
                        .map(ColumnInfo::getId)
                        .distinct()
                        .toList();
        if (matchedIds.isEmpty()) {
            return 0;
        }
        if (matchedIds.size() != normalizedColumnNames.size()) {
            throw new IllegalArgumentException(
                    "Some column semantic metadata does not exist for table "
                            + normalizedTableName
                            + ".");
        }
        return columnSemanticInfoMapper.deleteByDatasourceIdAndIds(datasourceId, matchedIds);
    }

    private void requireDatasource(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId) == null) {
            throw new IllegalArgumentException("Datasource does not exist: " + datasourceId);
        }
    }

    private ColumnSemanticResponse mapResponse(ColumnInfo columnInfo) {
        return new ColumnSemanticResponse(
                columnInfo.getId(),
                columnInfo.getColumnName(),
                null,
                columnInfo.getColumnDescription(),
                null,
                null,
                columnInfo.getIsVisible(),
                true,
                Boolean.TRUE.equals(columnInfo.getIsVisible()),
                null,
                columnInfo.getUpdateTime());
    }

    private String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

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
package io.github.malonetalk.service.semantic.relation;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.malonetalk.convertor.SemanticConverter;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.RelationWorkspacePageQuery;
import io.github.malonetalk.dto.semantic.RelationWorkspaceResponse;
import io.github.malonetalk.dto.semantic.RelationWorkspaceTableResponse;
import io.github.malonetalk.dto.semantic.TableSemanticPageQuery;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.utils.RequestAssert;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RelationWorkspaceService {
    private final DatasourceService datasourceService;
    private final DataPermissionService dataPermissions;
    private final UserContextHolder holder;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final LogicalTableRelationHelper logicalTableRelationHelper;
    private final SemanticConverter semanticConverter;

    public RelationWorkspaceResponse getRelationWorkspace(RelationWorkspacePageQuery query) {
        RequestAssert.requireNonNull(query.datasourceId(), "datasourceId cannot be null.");
        if (datasourceService.findById(query.datasourceId()) == null) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Datasource does not exist: " + query.datasourceId());
        }
        var user = holder.checkAndGet();
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        IPage<TableInfo> page =
                tableInfoMapper.selectPageByDatasourceId(
                        new Page<>(pageNumber, pageSize),
                        new TableSemanticPageQuery(
                                query.datasourceId(),
                                pageNumber,
                                pageSize,
                                SemanticUtils.trimToNull(query.keyword()),
                                query.sortOrder()),
                        SemanticUtils.isDescendingSort(query.sortOrder()),
                        dataPermissions.roleScope(user).orElse(null));
        if (page.getRecords().isEmpty()) {
            return new RelationWorkspaceResponse(
                    PageResponse.of(List.of(), page.getTotal(), pageNumber, pageSize), List.of());
        }

        Set<Integer> currentPageTableIds =
                page.getRecords().stream().map(TableInfo::getId).collect(Collectors.toSet());
        var scope = dataPermissions.loadScope(user, query.datasourceId());
        Map<Integer, List<ColumnInfo>> columnsByTableId =
                columnSemanticInfoMapper
                        .selectByDatasourceIdAndTableIds(query.datasourceId(), currentPageTableIds)
                        .stream()
                        .filter(c -> scope.canColumn(c.getTableName(), c.getColumnName()))
                        .collect(Collectors.groupingBy(ColumnInfo::getTableId));
        List<RelationWorkspaceTableResponse> nodes =
                page.getRecords().stream()
                        .map(
                                table ->
                                        semanticConverter.toWorkspaceTable(
                                                table,
                                                columnsByTableId.getOrDefault(
                                                        table.getId(), List.of())))
                        .toList();
        // Both endpoints must belong to the current page and be visible to the same permission
        // scope.
        List<LogicalTableRelationResponse> relations =
                logicalTableRelationMapper
                        .selectByDatasourceIdAndSourceTableIds(
                                query.datasourceId(), currentPageTableIds)
                        .stream()
                        .filter(
                                relation ->
                                        currentPageTableIds.contains(relation.getTargetTableId()))
                        .filter(relation -> logicalTableRelationHelper.isVisible(scope, relation))
                        .map(semanticConverter::toResponse)
                        .toList();
        return new RelationWorkspaceResponse(
                PageResponse.of(nodes, page.getTotal(), pageNumber, pageSize), relations);
    }
}

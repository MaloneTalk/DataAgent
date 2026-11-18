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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.malonetalk.convertor.SemanticConverter;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.prompt.TablePromptResponse;
import io.github.malonetalk.dto.semantic.TableSemanticPageQuery;
import io.github.malonetalk.dto.semantic.TableSemanticResponse;
import io.github.malonetalk.dto.semantic.TableSemanticUpdateRequest;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.bo.DataPermissionScopeBo;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.DatasourceWriteLockService;
import io.github.malonetalk.service.semantic.SemanticAvailabilityHelper;
import io.github.malonetalk.service.semantic.SemanticMergeService;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TableSemanticServiceImpl implements TableSemanticService {

    private final DatasourceService datasourceService;
    private final DataPermissionService dataPermissions;
    private final UserContextHolder holder;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;
    private final DatasourceWriteLockService datasourceLocks;
    private final SemanticMergeService semanticMergeService;
    private final SemanticConverter semanticConverter;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TableSemanticResponse> getTablePage(TableSemanticPageQuery query) {
        SemanticUtils.requireDatasourceId(query.datasourceId());
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        if (datasourceService.findById(query.datasourceId()).isEmpty()) {
            return PageResponse.empty(pageNumber, pageSize);
        }
        boolean sortDescending = SemanticUtils.isDescendingSort(query.sortOrder());
        var user = holder.checkAndGet();
        IPage<TableInfo> page =
                tableInfoMapper.selectPageByDatasourceId(
                        new Page<>(pageNumber, pageSize),
                        new TableSemanticPageQuery(
                                query.datasourceId(),
                                pageNumber,
                                pageSize,
                                SemanticUtils.trimToNull(query.keyword()),
                                query.sortOrder()),
                        sortDescending,
                        dataPermissions.roleScope(user).orElse(null));
        if (page.getRecords().isEmpty()) {
            return PageResponse.of(List.of(), page.getTotal(), pageNumber, pageSize);
        }
        var scope = dataPermissions.loadGrantedScope(user, query.datasourceId());
        Set<Integer> tableIds =
                page.getRecords().stream()
                        .filter(table -> !table.getBusinessKeyColumns().isEmpty())
                        .filter(SemanticAvailabilityHelper::hasPhysicalTable)
                        .filter(
                                table ->
                                        scope.canColumns(
                                                table.getTableName(),
                                                table.getBusinessKeyColumns()))
                        .map(TableInfo::getId)
                        .collect(Collectors.toSet());
        List<ColumnInfo> columns =
                tableIds.isEmpty()
                        ? List.of()
                        : columnSemanticInfoMapper.selectByDatasourceIdAndTableIds(
                                query.datasourceId(), tableIds);
        Map<Integer, Set<String>> physicalColumns =
                columns.stream()
                        .filter(SemanticAvailabilityHelper::hasPhysicalColumn)
                        .collect(
                                Collectors.groupingBy(
                                        ColumnInfo::getTableId,
                                        Collectors.mapping(
                                                column ->
                                                        SemanticUtils.normalizeObjectName(
                                                                column.getColumnName(),
                                                                "Missing columnName."),
                                                Collectors.toSet())));
        List<TableSemanticResponse> responses =
                page.getRecords().stream()
                        .map(
                                table ->
                                        semanticConverter.toResponse(
                                                table,
                                                physicalColumns.getOrDefault(
                                                        table.getId(), Set.of()),
                                                scope.canColumns(
                                                        table.getTableName(),
                                                        table.getBusinessKeyColumns())))
                        .toList();
        return PageResponse.of(responses, page.getTotal(), pageNumber, pageSize);
    }

    @Override
    public List<String> listTableNames(Integer datasourceId) {
        SemanticUtils.requireDatasourceId(datasourceId);
        if (datasourceService.findById(datasourceId).isEmpty()) {
            return List.of();
        }
        var scope = dataPermissions.loadScope(holder.checkAndGet(), datasourceId);
        return tableInfoMapper.selectByDatasourceId(datasourceId).stream()
                .filter(table -> scope.canTable(table.getTableName()))
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
        if (datasourceService.findById(datasourceId).isEmpty()) {
            return List.of();
        }
        var scope = dataPermissions.loadScope(holder.checkAndGet(), datasourceId);
        return tableInfoMapper.selectByDatasourceId(datasourceId).stream()
                .filter(table -> scope.canTable(table.getTableName()))
                .map(TableInfo::getDomain)
                .filter(domain -> domain != null && !domain.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    @Override
    public List<TablePromptResponse> listMergedTablesByDomains(
            Integer datasourceId, List<String> domains) {
        SemanticUtils.requireDatasourceId(datasourceId);
        return datasourceService
                .findById(datasourceId)
                .map(
                        datasource ->
                                semanticMergeService.listVisibleTablesByDomains(
                                        datasource, domains))
                .orElseGet(List::of);
    }

    @Override
    @Transactional
    public void updateTableSemantic(TableSemanticUpdateRequest request) {
        SemanticUtils.requireDatasourceId(request.datasourceId());
        datasourceLocks.lock(request.datasourceId());
        String tableName =
                SemanticUtils.requireTrimmed(
                        request.tableName(), "Missing tableName for table semantic update.");
        String normalizedTableName = tableName.toLowerCase(Locale.ROOT);
        var scope = dataPermissions.loadScope(holder.checkAndGet(), request.datasourceId());
        scope.requireTable(normalizedTableName);
        TableInfo existing =
                tableInfoMapper.selectByDatasourceIdAndTableName(
                        request.datasourceId(), normalizedTableName);
        if (existing == null) {
            TableInfo tableInfo = new TableInfo();
            tableInfo.setDatasourceId(request.datasourceId());
            tableInfo.setTableName(tableName);
            tableInfo.setTableDescription(SemanticUtils.trimToNull(request.tableDescription()));
            tableInfo.setDataGranularity(SemanticUtils.trimToNull(request.dataGranularity()));
            tableInfo.setDomain(SemanticUtils.normalizeDomain(request.domain()));
            tableInfo.setIsVisible(request.isVisible());
            tableInfo.setPhysicalStatus(Boolean.FALSE);
            Optional.ofNullable(request.businessKeyColumns())
                    .ifPresent(columns -> updateBusinessKey(tableInfo, columns, scope));
            tableInfo.setCreateTime(LocalDateTime.now());
            tableInfo.setUpdateTime(LocalDateTime.now());
            tableInfoMapper.insert(tableInfo);
            return;
        }
        Optional.ofNullable(request.businessKeyColumns())
                .ifPresent(columns -> updateBusinessKey(existing, columns, scope));
        existing.setTableDescription(SemanticUtils.trimToNull(request.tableDescription()));
        existing.setDataGranularity(SemanticUtils.trimToNull(request.dataGranularity()));
        existing.setDomain(SemanticUtils.normalizeDomain(request.domain()));
        existing.setIsVisible(request.isVisible());
        existing.setUpdateTime(LocalDateTime.now());
        tableInfoMapper.updateSemanticFields(existing);
    }

    private void updateBusinessKey(
            TableInfo table, List<String> requestedColumns, DataPermissionScopeBo scope) {
        if (!scope.canColumns(table.getTableName(), table.getBusinessKeyColumns())) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "无权修改包含未授权字段的业务唯一键");
        }
        List<String> normalized =
                requestedColumns.stream()
                        .map(name -> SemanticUtils.normalizeObjectName(name, "业务唯一键字段不能为空"))
                        .toList();
        if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "业务唯一键字段不能重复");
        }
        if (!scope.canColumns(table.getTableName(), normalized)) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "业务唯一键包含未授权字段");
        }
        // 未修改时保留已失效的配置，允许独立修改描述、粒度等语义信息。
        if (normalized.equals(
                table.getBusinessKeyColumns().stream()
                        .map(name -> SemanticUtils.normalizeObjectName(name, "业务唯一键字段不能为空"))
                        .toList())) {
            return;
        }
        if (normalized.isEmpty()) {
            table.setBusinessKeyColumns(List.of());
            return;
        }
        if (!SemanticAvailabilityHelper.hasPhysicalTable(table)) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "请先同步数据库对象后再配置业务唯一键");
        }
        Map<String, String> physicalColumns =
                columnSemanticInfoMapper
                        .selectByDatasourceIdAndTableName(
                                table.getDatasourceId(), table.getTableName())
                        .stream()
                        .filter(SemanticAvailabilityHelper::hasPhysicalColumn)
                        .collect(
                                Collectors.toMap(
                                        column ->
                                                SemanticUtils.normalizeObjectName(
                                                        column.getColumnName(),
                                                        "Missing columnName."),
                                        ColumnInfo::getColumnName));
        if (!physicalColumns.keySet().containsAll(normalized)) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "业务唯一键只能使用当前已同步且存在的字段");
        }
        table.setBusinessKeyColumns(normalized.stream().map(physicalColumns::get).toList());
    }

    @Override
    @Transactional
    public void resetTableSemantic(Integer datasourceId, String tableName) {
        SemanticUtils.requireDatasourceId(datasourceId);
        datasourceLocks.lock(datasourceId);
        String normalizedTableName =
                SemanticUtils.requireTrimmed(
                        tableName, "Missing tableName for table semantic reset.");
        var scope = dataPermissions.loadScope(holder.checkAndGet(), datasourceId);
        scope.requireTable(normalizedTableName);
        TableInfo existing =
                tableInfoMapper.selectByDatasourceIdAndTableName(datasourceId, normalizedTableName);
        if (existing == null) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND, "Table semantic metadata does not exist.");
        }
        resetTableRecords(datasourceId, List.of(existing), scope);
    }

    @Override
    @Transactional
    public int resetTableSemantics(Integer datasourceId, List<String> tableNames) {
        SemanticUtils.requireDatasourceId(datasourceId);
        datasourceLocks.lock(datasourceId);
        if (tableNames == null || tableNames.isEmpty()) {
            return 0;
        }
        Set<String> normalizedNames =
                tableNames.stream()
                        .map(
                                name ->
                                        SemanticUtils.normalizeObjectName(
                                                name,
                                                "Missing tableName for batch table semantic"
                                                        + " reset."))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var scope = dataPermissions.loadScope(holder.checkAndGet(), datasourceId);
        normalizedNames.forEach(scope::requireTable);
        List<TableInfo> matchedTables =
                tableInfoMapper.selectByDatasourceIdAndTableNames(
                        datasourceId, List.copyOf(normalizedNames));
        if (matchedTables.isEmpty()) {
            return 0;
        }
        if (matchedTables.size() != normalizedNames.size()) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Some table semantic metadata does not exist for datasource "
                            + datasourceId
                            + ".");
        }
        return resetTableRecords(datasourceId, matchedTables, scope);
    }

    private int resetTableRecords(
            Integer datasourceId, List<TableInfo> tables, DataPermissionScopeBo scope) {
        List<Integer> resetIds = new ArrayList<>();
        List<Integer> deleteIds = new ArrayList<>();
        // 仅删除明确标记为物理缺失的表；其他记录保留并重置语义字段。
        for (TableInfo table : tables) {
            if (!scope.canColumns(table.getTableName(), table.getBusinessKeyColumns())) {
                throw BusinessException.of(ErrorCode.FORBIDDEN, "无权重置包含未授权字段的业务唯一键");
            }
            (Boolean.FALSE.equals(table.getPhysicalStatus()) ? deleteIds : resetIds)
                    .add(table.getId());
        }
        int affected = 0;
        if (!deleteIds.isEmpty()) {
            affected += tableInfoMapper.deletePhysicalMissingByIds(datasourceId, deleteIds);
        }
        if (!resetIds.isEmpty()) {
            affected += tableInfoMapper.resetSemanticFieldsByIds(datasourceId, resetIds);
        }
        return affected;
    }
}

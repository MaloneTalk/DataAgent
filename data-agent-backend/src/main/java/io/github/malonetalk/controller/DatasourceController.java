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
package io.github.malonetalk.controller;

import io.github.malonetalk.annotation.RequirePermission;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.converter.DatasourceConverter;
import io.github.malonetalk.model.dto.DatasourceDto;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.vo.BooleanVo;
import io.github.malonetalk.model.vo.DatasourceColumnsVo;
import io.github.malonetalk.model.vo.DatasourceObjectVo;
import io.github.malonetalk.model.vo.DatasourceOptionVo;
import io.github.malonetalk.model.vo.DatasourceVo;
import io.github.malonetalk.model.vo.ListVo;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/datasource")
public class DatasourceController {

    private final DatasourceService dataSourceService;
    private final DataPermissionService dataPermissions;
    private final UserContextHolder holder;
    private final DatasourceConverter datasourceConverter;

    /**
     * TableInfoMapper 和 ColumnSemanticInfoMapper 直接注入 Controller 而非通过 Service：
     *
     * <p>{@code GET /{id}/tables} 和 {@code GET /{id}/columns} 仅做"查 mapper → 返回"的纯透传，
     * 零业务逻辑（无 join、无事务、无缓存、无校验）。引入中间 Service 只会产生一层无意义的委托代码。
     *
     * <p>如果后续需要按角色过滤返回结果（权限控制），届时再将两个查询收拢到 TableMetadataService。
     */
    private final TableInfoMapper tableInfoMapper;

    private final ColumnSemanticInfoMapper columnSemanticInfoMapper;

    @GetMapping
    @RequirePermission(PermissionEnum.DATA_DATASOURCE_READ)
    public ListVo<DatasourceVo> findAll() {
        List<DatasourceVo> list =
                dataSourceService.findAll().stream().map(datasourceConverter::toVo).toList();
        return new ListVo<>(list);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionEnum.DATA_DATASOURCE_READ)
    public DatasourceVo findById(@PathVariable Integer id) {
        return datasourceConverter.toVo(dataSourceService.requireById(id));
    }

    /** 保留表和视图名称列表的兼容格式。 */
    @GetMapping("/{id}/tables")
    @RequirePermission(PermissionEnum.SYS_ROLE_GRANT)
    public ListVo<String> listTableNames(@PathVariable Integer id) {
        dataSourceService.requireById(id);
        List<String> tables =
                tableInfoMapper.selectByDatasourceId(id).stream()
                        .map(TableInfo::getTableName)
                        .toList();
        return new ListVo<>(tables);
    }

    @GetMapping("/{id}/objects")
    @RequirePermission(PermissionEnum.SYS_ROLE_GRANT)
    public ListVo<DatasourceObjectVo> listObjects(@PathVariable Integer id) {
        dataSourceService.requireById(id);
        return new ListVo<>(
                tableInfoMapper.selectByDatasourceId(id).stream()
                        .map(
                                table ->
                                        new DatasourceObjectVo(
                                                table.getTableName(), table.getObjectType()))
                        .toList());
    }

    /** 返回某数据源下所有表的列名，一次查询，供列级权限配置使用。 */
    @GetMapping("/{id}/columns")
    @RequirePermission(PermissionEnum.SYS_ROLE_GRANT)
    public DatasourceColumnsVo listAllColumns(@PathVariable Integer id) {
        dataSourceService.requireById(id);
        Map<String, List<String>> map =
                columnSemanticInfoMapper.selectByDatasourceId(id).stream()
                        .collect(
                                Collectors.groupingBy(
                                        ColumnInfo::getTableName,
                                        Collectors.mapping(
                                                ColumnInfo::getColumnName, Collectors.toList())));
        return new DatasourceColumnsVo(map);
    }

    @PostMapping
    @RequirePermission(PermissionEnum.DATA_DATASOURCE_CREATE)
    public BooleanVo save(@Valid @RequestBody DatasourceDto request) {
        dataSourceService.save(request);
        return BooleanVo.TRUE;
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionEnum.DATA_DATASOURCE_UPDATE)
    public BooleanVo update(@PathVariable Integer id, @Valid @RequestBody DatasourceDto request) {
        dataSourceService.update(id, request);
        return BooleanVo.TRUE;
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionEnum.DATA_DATASOURCE_DELETE)
    public BooleanVo deleteById(@PathVariable Integer id) {
        dataSourceService.deleteById(id);
        return BooleanVo.TRUE;
    }

    @GetMapping("/status/{status}")
    @RequirePermission(PermissionEnum.DATA_DATASOURCE_READ)
    public ListVo<DatasourceVo> findByStatus(@PathVariable String status) {
        List<DatasourceVo> list =
                dataSourceService.findByStatus(status).stream()
                        .map(datasourceConverter::toVo)
                        .toList();
        return new ListVo<>(list);
    }

    @GetMapping("/type/{type}")
    @RequirePermission(PermissionEnum.DATA_DATASOURCE_READ)
    public ListVo<DatasourceVo> findByType(@PathVariable String type) {
        List<DatasourceVo> list =
                dataSourceService.findByType(type).stream().map(datasourceConverter::toVo).toList();
        return new ListVo<>(list);
    }

    @PutMapping("/{id}/activate")
    @RequirePermission(PermissionEnum.DATA_DATASOURCE_SET_STATUS)
    public BooleanVo activate(@PathVariable Integer id) {
        dataSourceService.updateStatus(id, Status.ACTIVE);
        return BooleanVo.TRUE;
    }

    @PutMapping("/{id}/deactivate")
    @RequirePermission(PermissionEnum.DATA_DATASOURCE_SET_STATUS)
    public BooleanVo deactivate(@PathVariable Integer id) {
        dataSourceService.updateStatus(id, Status.INACTIVE);
        return BooleanVo.TRUE;
    }

    @GetMapping("/options")
    @RequirePermission(
            value = {
                PermissionEnum.ANALYSIS_CHAT_USE,
                PermissionEnum.SEMANTIC_TABLE_READ,
                PermissionEnum.SEMANTIC_RELATION_READ,
                PermissionEnum.SEMANTIC_METRIC_READ
            },
            anyOf = true)
    public ListVo<DatasourceOptionVo> options() {
        var user = holder.checkAndGet();
        return new ListVo<>(
                dataSourceService.findAll().stream()
                        .filter(ds -> dataPermissions.canDatasource(user, ds.getId()))
                        .map(
                                ds ->
                                        new DatasourceOptionVo(
                                                ds.getId(),
                                                ds.getName(),
                                                ds.getType(),
                                                ds.getStatus()))
                        .toList());
    }
}

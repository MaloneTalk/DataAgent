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

import io.github.malonetalk.common.Result;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.service.TableInfoService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tableinfo")
public class TableInfoController {

    private final TableInfoService tableInfoService;

    public TableInfoController(TableInfoService tableInfoService) {
        this.tableInfoService = tableInfoService;
    }

    @GetMapping
    public Result<List<TableInfo>> findAll() {
        List<TableInfo> list = tableInfoService.findAll();
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<TableInfo> findById(@PathVariable Integer id) {
        TableInfo tableInfo = tableInfoService.findById(id);
        if (tableInfo != null) {
            return Result.success(tableInfo);
        } else {
            return Result.error(404, "TableInfo not found");
        }
    }

    @PostMapping
    public Result<Boolean> save(@RequestBody TableInfo tableInfo) {
        boolean success = tableInfoService.save(tableInfo);
        return success ? Result.success(true) : Result.error("Failed to save");
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody TableInfo tableInfo) {
        boolean success = tableInfoService.update(tableInfo);
        return success ? Result.success(true) : Result.error("Failed to update");
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteById(@PathVariable Integer id) {
        boolean success = tableInfoService.deleteById(id);
        return success ? Result.success(true) : Result.error("Failed to delete");
    }

    @GetMapping("/datasource/{datasourceId}")
    public Result<List<TableInfo>> findByDatasourceId(@PathVariable Integer datasourceId) {
        List<TableInfo> list = tableInfoService.findByDatasourceId(datasourceId);
        return Result.success(list);
    }

    @GetMapping("/active/{isActive}")
    public Result<List<TableInfo>> findByIsActive(@PathVariable Boolean isActive) {
        List<TableInfo> list = tableInfoService.findByIsActive(isActive);
        return Result.success(list);
    }

    @GetMapping("/datasource/{datasourceId}/active/{isActive}")
    public Result<List<TableInfo>> findByDatasourceIdAndIsActive(
            @PathVariable Integer datasourceId, @PathVariable Boolean isActive) {
        List<TableInfo> list =
                tableInfoService.findByDatasourceIdAndIsActive(datasourceId, isActive);
        return Result.success(list);
    }
}

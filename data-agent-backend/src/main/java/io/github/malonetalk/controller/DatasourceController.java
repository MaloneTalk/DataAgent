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
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.service.DatasourceService;
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
@RequestMapping("/api/datasource")
public class DatasourceController {

    private final DatasourceService dataSourceService;

    public DatasourceController(DatasourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    public Result<List<Datasource>> findAll() {
        List<Datasource> list = dataSourceService.findAll();
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<Datasource> findById(@PathVariable Integer id) {
        Datasource dataSource = dataSourceService.findById(id);
        if (dataSource != null) {
            return Result.success(dataSource);
        } else {
            return Result.error(404, "DataSource not found");
        }
    }

    @PostMapping
    public Result<Boolean> save(@RequestBody Datasource dataSource) {
        boolean success = dataSourceService.save(dataSource);
        return success ? Result.success(true) : Result.error("Failed to save");
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody Datasource dataSource) {
        boolean success = dataSourceService.update(dataSource);
        return success ? Result.success(true) : Result.error("Failed to update");
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteById(@PathVariable Integer id) {
        boolean success = dataSourceService.deleteById(id);
        return success ? Result.success(true) : Result.error("Failed to delete");
    }

    @GetMapping("/status/{status}")
    public Result<List<Datasource>> findByStatus(@PathVariable String status) {
        List<Datasource> list = dataSourceService.findByStatus(status);
        return Result.success(list);
    }

    @GetMapping("/type/{type}")
    public Result<List<Datasource>> findByType(@PathVariable String type) {
        List<Datasource> list = dataSourceService.findByType(type);
        return Result.success(list);
    }
}

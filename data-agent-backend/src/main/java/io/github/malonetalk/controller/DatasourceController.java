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
import io.github.malonetalk.convertor.DatasourceConverter;
import io.github.malonetalk.dto.DatasourceActivateRequest;
import io.github.malonetalk.dto.DatasourceRequest;
import io.github.malonetalk.dto.DatasourceResponse;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.service.DatasourceService;
import jakarta.validation.Valid;
import java.util.List;
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
    private final DatasourceConverter datasourceConverter;

    @GetMapping
    public Result<List<DatasourceResponse>> findAll() {
        List<DatasourceResponse> list =
                dataSourceService.findAll().stream().map(datasourceConverter::toResponse).toList();
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<DatasourceResponse> findById(@PathVariable Integer id) {
        Datasource datasource = dataSourceService.findById(id);
        if (datasource == null) {
            return Result.error(404, "DataSource not found");
        }
        return Result.success(datasourceConverter.toResponse(datasource));
    }

    @PostMapping
    public Result<Boolean> save(@Valid @RequestBody DatasourceRequest request) {
        Datasource datasource = datasourceConverter.toEntity(request);
        datasource.setStatus(Status.INACTIVE.getCode());
        boolean success = dataSourceService.save(datasource);
        return success ? Result.success() : Result.error("Failed to save");
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(
            @PathVariable Integer id, @Valid @RequestBody DatasourceRequest request) {
        Datasource datasource = dataSourceService.findById(id);
        if (datasource == null) {
            return Result.error(404, "DataSource not found");
        }
        datasource.setName(request.name());
        datasource.setType(request.type());
        datasource.setHost(request.host());
        datasource.setPort(request.port());
        datasource.setDatabaseName(request.databaseName());
        datasource.setUsername(request.username());
        if (request.password() != null && !request.password().isEmpty()) {
            datasource.setPassword(request.password());
        }
        datasource.setConnectionUrl(request.connectionUrl());
        datasource.setDescription(request.description());
        boolean success = dataSourceService.update(datasource);
        return success ? Result.success(true) : Result.error("Failed to update");
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteById(@PathVariable Integer id) {
        boolean success = dataSourceService.deleteById(id);
        return success ? Result.success(true) : Result.error("Failed to delete");
    }

    @GetMapping("/status/{status}")
    public Result<List<DatasourceResponse>> findByStatus(@PathVariable String status) {
        List<DatasourceResponse> list =
                dataSourceService.findByStatus(status).stream()
                        .map(datasourceConverter::toResponse)
                        .toList();
        return Result.success(list);
    }

    @GetMapping("/type/{type}")
    public Result<List<DatasourceResponse>> findByType(@PathVariable String type) {
        List<DatasourceResponse> list =
                dataSourceService.findByType(type).stream()
                        .map(datasourceConverter::toResponse)
                        .toList();
        return Result.success(list);
    }

    @PutMapping("/{id}/activate")
    public Result<Boolean> activate(
            @PathVariable Integer id,
            @RequestBody(required = false) DatasourceActivateRequest request) {
        Datasource datasource = dataSourceService.findById(id);
        if (datasource == null) {
            return Result.error(404, "DataSource not found");
        }
        boolean success =
                dataSourceService.activate(id, request == null ? null : request.activeDomains());
        return success ? Result.success(true) : Result.error("Activate failed");
    }

    @PutMapping("/{id}/deactivate")
    public Result<Boolean> deactivate(@PathVariable Integer id) {
        Datasource datasource = dataSourceService.findById(id);
        if (datasource == null) {
            return Result.error(404, "DataSource not found");
        }
        boolean success = dataSourceService.updateStatus(id, Status.INACTIVE.getCode());
        return success ? Result.success(true) : Result.error("Deactivate failed");
    }
}

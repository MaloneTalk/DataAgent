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

import io.github.malonetalk.agent.datasource.DataSourceType;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.Result;
import io.github.malonetalk.convertor.DatasourceConverter;
import io.github.malonetalk.dto.DatasourceRequest;
import io.github.malonetalk.dto.DatasourceResponse;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.exception.BusinessException;
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
        return Result.success(datasourceConverter.toResponse(requireDatasource(id)));
    }

    @PostMapping
    public Result<Boolean> save(@Valid @RequestBody DatasourceRequest request) {
        DataSourceType type = requireDatasourceType(request.type());
        Datasource datasource = datasourceConverter.toEntity(request);
        datasource.setType(type.getCode());
        datasource.setStatus(Status.INACTIVE.getCode());
        requireOperationSuccess(
                dataSourceService.save(datasource), "Failed to save the datasource.");
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(
            @PathVariable Integer id, @Valid @RequestBody DatasourceRequest request) {
        DataSourceType type = requireDatasourceType(request.type());
        Datasource datasource = requireDatasource(id);
        datasource.setName(request.name());
        datasource.setType(type.getCode());
        datasource.setHost(request.host());
        datasource.setPort(request.port());
        datasource.setDatabaseName(request.databaseName());
        datasource.setUsername(request.username());
        if (request.password() != null && !request.password().isEmpty()) {
            datasource.setPassword(request.password());
        }
        datasource.setConnectionUrl(request.connectionUrl());
        datasource.setDescription(request.description());
        requireOperationSuccess(
                dataSourceService.update(datasource), "Failed to update the datasource.");
        return Result.success(true);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteById(@PathVariable Integer id) {
        requireDatasource(id);
        requireOperationSuccess(
                dataSourceService.deleteById(id), "Failed to delete the datasource.");
        return Result.success(true);
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
    public Result<Boolean> activate(@PathVariable Integer id) {
        requireDatasource(id);
        requireOperationSuccess(
                dataSourceService.updateStatus(id, Status.ACTIVE.getCode()),
                "Failed to activate the datasource.");
        return Result.success(true);
    }

    @PutMapping("/{id}/deactivate")
    public Result<Boolean> deactivate(@PathVariable Integer id) {
        requireDatasource(id);
        requireOperationSuccess(
                dataSourceService.updateStatus(id, Status.INACTIVE.getCode()),
                "Failed to deactivate the datasource.");
        return Result.success(true);
    }

    private Datasource requireDatasource(Integer id) {
        Datasource datasource = dataSourceService.findById(id);
        if (datasource == null) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "Datasource not found.");
        }
        return datasource;
    }

    private DataSourceType requireDatasourceType(String type) {
        return DataSourceType.fromCode(type)
                .orElseThrow(
                        () ->
                                BusinessException.of(
                                        ErrorCode.UNSUPPORTED_DATASOURCE_TYPE,
                                        "Unsupported datasource type: " + type));
    }

    private void requireOperationSuccess(boolean success, String message) {
        if (!success) {
            throw BusinessException.of(ErrorCode.OPERATION_FAILED, message);
        }
    }
}

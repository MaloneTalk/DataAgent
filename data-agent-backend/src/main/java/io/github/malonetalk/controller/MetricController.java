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
import io.github.malonetalk.convertor.MetricConverter;
import io.github.malonetalk.dto.MetricRequest;
import io.github.malonetalk.dto.MetricResponse;
import io.github.malonetalk.entity.MetricInfo;
import io.github.malonetalk.service.MetricService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@AllArgsConstructor
@Validated
@RequestMapping("/api/metric")
public class MetricController {

    private final MetricService metricService;
    private final MetricConverter metricConverter;

    @PostMapping
    public Result<MetricResponse> create(@Valid @RequestBody MetricRequest request) {
        MetricInfo entity = metricConverter.toEntity(request);
        return Result.success(metricConverter.toResponse(metricService.create(entity)));
    }

    @PutMapping("/{id}")
    public Result<MetricResponse> update(
            @PathVariable @Positive(message = "id 必须为正数") Integer id,
            @Valid @RequestBody MetricRequest request) {
        MetricInfo entity = metricConverter.toEntity(request);
        return Result.success(metricConverter.toResponse(metricService.update(id, entity)));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable @Positive(message = "id 必须为正数") Integer id) {
        metricService.delete(id);
        return Result.success(true);
    }

    @GetMapping("/{id}")
    public Result<MetricResponse> getById(
            @PathVariable @Positive(message = "id 必须为正数") Integer id) {
        return Result.success(metricConverter.toResponse(metricService.getById(id)));
    }

    @GetMapping("/key/{metricKey}")
    public Result<MetricResponse> getByKey(@PathVariable String metricKey) {
        return Result.success(metricConverter.toResponse(metricService.getByKey(metricKey)));
    }

    @GetMapping
    public Result<List<MetricResponse>> listAll() {
        return Result.success(
                metricService.listAll().stream().map(metricConverter::toResponse).toList());
    }
}

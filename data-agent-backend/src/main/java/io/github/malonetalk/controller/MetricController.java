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
import io.github.malonetalk.entity.MetricInfo;
import io.github.malonetalk.service.MetricService;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/api/metric")
public class MetricController {

    private final MetricService metricService;

    @PostMapping
    public Result<MetricInfo> create(@RequestBody MetricInfo metricInfo) {
        return Result.success(metricService.create(metricInfo));
    }

    @PutMapping("/{id}")
    public Result<MetricInfo> update(@PathVariable Integer id, @RequestBody MetricInfo metricInfo) {
        return Result.success(metricService.update(id, metricInfo));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Integer id) {
        metricService.delete(id);
        return Result.success(true);
    }

    @GetMapping("/{id}")
    public Result<MetricInfo> getById(@PathVariable Integer id) {
        return Result.success(metricService.getById(id));
    }

    @GetMapping("/key/{metricKey}")
    public Result<MetricInfo> getByKey(@PathVariable String metricKey) {
        return Result.success(metricService.getByKey(metricKey));
    }

    @GetMapping
    public Result<List<MetricInfo>> listAll() {
        return Result.success(metricService.listAll());
    }
}

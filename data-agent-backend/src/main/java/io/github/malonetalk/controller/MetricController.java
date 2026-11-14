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

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.malonetalk.annotation.RequirePermission;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.model.converter.BatchQueryConverter;
import io.github.malonetalk.model.converter.MetricConverter;
import io.github.malonetalk.model.dto.MetricCreateDto;
import io.github.malonetalk.model.dto.MetricQueryDto;
import io.github.malonetalk.model.dto.MetricUpdateDto;
import io.github.malonetalk.model.vo.BatchQueryVo;
import io.github.malonetalk.model.vo.BooleanVo;
import io.github.malonetalk.model.vo.MetricInfoVo;
import io.github.malonetalk.service.MetricService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RequestMapping("/api/metric")
public class MetricController {

    private final MetricService metricService;
    private final MetricConverter metricConverter;

    @PostMapping
    @RequirePermission(PermissionEnum.SEMANTIC_METRIC_CREATE)
    public MetricInfoVo create(@Valid @RequestBody MetricCreateDto dto) {
        return metricConverter.toVo(metricService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionEnum.SEMANTIC_METRIC_UPDATE)
    public MetricInfoVo update(
            @PathVariable @Positive(message = "id 必须为正数") Integer id,
            @Valid @RequestBody MetricUpdateDto dto) {
        return metricConverter.toVo(metricService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionEnum.SEMANTIC_METRIC_DELETE)
    public BooleanVo delete(@PathVariable @Positive(message = "id 必须为正数") Integer id) {
        metricService.delete(id);
        return BooleanVo.TRUE;
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionEnum.SEMANTIC_METRIC_READ)
    public MetricInfoVo getById(@PathVariable @Positive(message = "id 必须为正数") Integer id) {
        return metricConverter.toVo(metricService.getById(id));
    }

    @GetMapping
    @RequirePermission(PermissionEnum.SEMANTIC_METRIC_READ)
    public BatchQueryVo<MetricInfoVo> list(@Valid MetricQueryDto dto) {
        IPage<MetricInfoVo> page = metricService.page(dto).convert(metricConverter::toVo);
        return BatchQueryConverter.toVo(page);
    }
}

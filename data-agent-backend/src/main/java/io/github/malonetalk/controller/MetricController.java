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
import io.github.malonetalk.model.converter.BatchQueryConverter;
import io.github.malonetalk.model.converter.MetricConverter;
import io.github.malonetalk.model.dto.BaseBatchQueryDto;
import io.github.malonetalk.model.dto.MetricCreateDto;
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

    @RequirePermission
    @PostMapping
    public MetricInfoVo create(@Valid @RequestBody MetricCreateDto dto) {
        return metricConverter.toVo(metricService.create(dto));
    }

    @RequirePermission
    @PutMapping("/{id}")
    public MetricInfoVo update(
            @PathVariable @Positive(message = "id 必须为正数") Integer id,
            @Valid @RequestBody MetricUpdateDto dto) {
        return metricConverter.toVo(metricService.update(id, dto));
    }

    @RequirePermission
    @DeleteMapping("/{id}")
    public BooleanVo delete(@PathVariable @Positive(message = "id 必须为正数") Integer id) {
        metricService.delete(id);
        return BooleanVo.TRUE;
    }

    @GetMapping("/{id}")
    public MetricInfoVo getById(@PathVariable @Positive(message = "id 必须为正数") Integer id) {
        return metricConverter.toVo(metricService.getById(id));
    }

    @GetMapping
    public BatchQueryVo<MetricInfoVo> list(@Valid BaseBatchQueryDto dto) {
        IPage<MetricInfoVo> page = metricService.page(dto).convert(metricConverter::toVo);
        return BatchQueryConverter.toVo(page);
    }
}

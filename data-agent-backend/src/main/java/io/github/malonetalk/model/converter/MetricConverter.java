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
package io.github.malonetalk.model.converter;

import io.github.malonetalk.model.bo.MetricInfoBo;
import io.github.malonetalk.model.dto.MetricCreateDto;
import io.github.malonetalk.model.dto.MetricUpdateDto;
import io.github.malonetalk.model.po.MetricInfoPo;
import io.github.malonetalk.model.vo.MetricInfoVo;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MetricConverter {

    MetricInfoBo toBo(MetricInfoPo po);

    MetricInfoVo toVo(MetricInfoBo bo);

    /** insert 用：只映射客户端可编辑字段，datasourceId/metricKey 由 Service 归一化后设置。 */
    @Mapping(target = "metricKey", ignore = true)
    MetricInfoPo toPoForInsert(MetricCreateDto dto);

    /** update 用：仅覆盖 DTO 中非 null 字段，未提供的字段保持原值（空串可清空）。 */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void toPoForUpdate(MetricUpdateDto dto, @MappingTarget MetricInfoPo po);
}

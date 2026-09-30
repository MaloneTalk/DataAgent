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
package io.github.malonetalk.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.malonetalk.model.bo.MetricInfoBo;
import io.github.malonetalk.model.dto.BaseBatchQueryDto;
import io.github.malonetalk.model.dto.MetricCreateDto;
import io.github.malonetalk.model.dto.MetricUpdateDto;

/** 指标口径业务：以 {@link MetricInfoBo} 作为领域对象。 */
public interface MetricService {

    /** 供 agent 工具调用:按自然语言提示返回指标口径文本(含命中/多候选/未命中三种结果)。 */
    String getCaliberByHint(String hint);

    MetricInfoBo create(MetricCreateDto dto);

    MetricInfoBo update(Integer id, MetricUpdateDto dto);

    void delete(Integer id);

    MetricInfoBo getById(Integer id);

    IPage<MetricInfoBo> page(BaseBatchQueryDto dto);
}

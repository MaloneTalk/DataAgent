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

import io.github.malonetalk.entity.MetricInfo;
import java.util.List;

public interface MetricService {

    /** 供 agent 工具调用:按自然语言提示返回指标口径文本(含命中/多候选/未命中三种结果)。 */
    String getCaliberByHint(String hint);

    MetricInfo create(MetricInfo metricInfo);

    MetricInfo update(Integer id, MetricInfo metricInfo);

    void delete(Integer id);

    MetricInfo getById(Integer id);

    MetricInfo getByKey(String metricKey);

    List<MetricInfo> listAll();
}

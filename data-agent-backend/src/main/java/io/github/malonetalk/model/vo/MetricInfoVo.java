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
package io.github.malonetalk.model.vo;

import java.time.LocalDateTime;

/** 指标口径出参。与持久化对象解耦,不泄露 isDeleted 等内部状态。 */
public record MetricInfoVo(
        Integer id,
        Integer datasourceId,
        String metricKey,
        String name,
        String aliases,
        String measureExpr,
        String filters,
        String timeField,
        String description,
        LocalDateTime createTime,
        LocalDateTime updateTime)
        implements BaseVo {}

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
package io.github.malonetalk.model.dto;

import jakarta.validation.constraints.NotBlank;

/** 更新指标口径。不包含 metricKey,即不允许修改稳定标识。 */
public record MetricUpdateDto(
        @NotBlank(message = "name 不能为空") String name,
        String aliases,
        String measureExpr,
        String filters,
        String timeField,
        String description) {}

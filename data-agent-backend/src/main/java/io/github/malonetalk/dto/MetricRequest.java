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
package io.github.malonetalk.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 指标口径的入参边界类。只暴露客户端可编辑的字段,不暴露 id/时间/逻辑删除等由系统控制的列,
 * 避免实体直收请求体导致的越权赋值(over-posting)。
 */
public record MetricRequest(
        @NotBlank(message = "metricKey 不能为空") String metricKey,
        @NotBlank(message = "name 不能为空") String name,
        String aliases,
        String measureExpr,
        String filters,
        String timeField,
        String description) {}

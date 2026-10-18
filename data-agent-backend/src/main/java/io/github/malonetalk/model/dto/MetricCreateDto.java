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

import io.github.malonetalk.enums.MetricCalculationType;
import io.github.malonetalk.enums.MetricValueType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Builder;

/** 创建指标口径。只暴露客户端可编辑字段,id/时间/逻辑删除由系统控制。 */
@Builder
public record MetricCreateDto(
        @NotNull(message = "datasourceId 不能为空") @Min(value = 1, message = "datasourceId 必须为正数")
                Integer datasourceId,
        @NotBlank(message = "metricKey 不能为空") String metricKey,
        @NotBlank(message = "name 不能为空") String name,
        String aliases,
        String measureExpr,
        String filters,
        String timeField,
        MetricCalculationType calculationType,
        MetricValueType valueType,
        @Size(max = 32, message = "unit 最多32个字符") String unit,
        @DecimalMin(value = "0", inclusive = false, message = "resultMultiplier 必须大于0")
                @Digits(integer = 12, fraction = 12, message = "resultMultiplier 最多12位整数和12位小数")
                BigDecimal resultMultiplier,
        @Pattern(regexp = "^$|[A-Za-z]{3}", message = "currencyCode 必须为3位币种代码") String currencyCode,
        String description) {}

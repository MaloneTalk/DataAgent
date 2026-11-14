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
package io.github.malonetalk.model.bo;

import io.github.malonetalk.enums.MetricCalculationType;
import io.github.malonetalk.enums.MetricValueType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 指标口径领域对象。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricInfoBo {

    private Integer id;
    private Integer datasourceId;
    private String metricKey;
    private String name;
    private String aliases;
    private String measureExpr;
    private String filters;
    private String timeField;
    private MetricCalculationType calculationType;
    private MetricValueType valueType;
    private String unit;
    private BigDecimal resultMultiplier;
    private String currencyCode;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

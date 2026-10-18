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
import java.util.Objects;
import java.util.Optional;
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

    /**
     * 渲染指标口径文本,供大模型在合成 SQL 时消费。
     * 仅依赖自身字段,无外部依赖,因此放在领域对象内部而非 Service。
     */
    public String toCaliberText() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("指标口径: %s (%s)%n", name, metricKey));
        if (aliases != null) {
            sb.append(String.format("同义词: %s%n", aliases));
        }
        if (measureExpr != null) {
            sb.append(String.format("度量: %s%n", measureExpr));
        }
        if (filters != null) {
            sb.append(String.format("过滤: %s%n", filters));
        }
        if (timeField != null) {
            sb.append(String.format("时间字段: %s%n", timeField));
        }
        appendBusinessProperties(sb);
        if (description != null) {
            sb.append(String.format("说明: %s%n", description));
        }
        return sb.toString();
    }

    private void appendBusinessProperties(StringBuilder sb) {
        MetricCalculationType calculation =
                Optional.ofNullable(calculationType).orElse(MetricCalculationType.EXPRESSION);
        sb.append("计算方式: ").append(calculation.getDescription()).append('\n');
        sb.append("结果类型: ")
                .append(
                        Optional.ofNullable(valueType)
                                .map(MetricValueType::getDescription)
                                .orElse("未配置"))
                .append('\n');
        sb.append("标准结果单位: ").append(Objects.toString(unit, "未配置")).append('\n');
        sb.append("结果换算: 标准结果 = 度量表达式原始结果 × ")
                .append(
                        Objects.requireNonNullElse(resultMultiplier, BigDecimal.ONE)
                                .toPlainString())
                .append("；仅换算一次。\n");
        if (valueType == MetricValueType.MONEY) {
            sb.append("币种: ")
                    .append(Objects.toString(currencyCode, "未配置"))
                    .append("；仅汇总同币种数据，混合币种须先过滤或明确换汇规则。\n");
        }
        if (valueType == MetricValueType.PERCENT) {
            sb.append("百分比以比值计算，展示时乘以100并添加%，例如0.1234展示为12.34%；不要重复换算。\n");
        }
        switch (calculation) {
            case DISTINCT_COUNT -> sb.append("去重计数应在目标区间重新去重，不可相加每日去重结果。\n");
            case RATIO -> sb.append("分子分母应按目标区间重新计算，不可直接求和或简单平均每日比率；零分母规则以表达式及说明为准。\n");
            case SNAPSHOT -> sb.append("快照不可跨时间直接相加；快照时点及缺失处理须按表达式与说明确认，未明确时先澄清。\n");
            case EXPRESSION -> {}
        }
        sb.append("计算方式为口径元数据，请结合表结构和度量表达式生成SQL。\n");
    }
}

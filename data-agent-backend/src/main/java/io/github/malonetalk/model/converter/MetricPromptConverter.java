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

import io.github.malonetalk.enums.MetricCalculationType;
import io.github.malonetalk.enums.MetricValueType;
import io.github.malonetalk.model.bo.MetricInfoBo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MetricPromptConverter {

    public String convert(MetricInfoBo metric) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("指标口径: %s (%s)%n", metric.getName(), metric.getMetricKey()));
        if (metric.getAliases() != null) {
            sb.append(String.format("同义词: %s%n", metric.getAliases()));
        }
        if (metric.getMeasureExpr() != null) {
            sb.append(String.format("度量: %s%n", metric.getMeasureExpr()));
        }
        if (metric.getFilters() != null) {
            sb.append(String.format("过滤: %s%n", metric.getFilters()));
        }
        if (metric.getTimeField() != null) {
            sb.append(String.format("时间字段: %s%n", metric.getTimeField()));
        }
        appendBusinessProperties(sb, metric);
        if (metric.getDescription() != null) {
            sb.append(String.format("说明: %s%n", metric.getDescription()));
        }
        return sb.toString();
    }

    private void appendBusinessProperties(StringBuilder sb, MetricInfoBo metric) {
        MetricCalculationType calculation =
                Optional.ofNullable(metric.getCalculationType())
                        .orElse(MetricCalculationType.EXPRESSION);
        sb.append("计算方式: ").append(calculation.getDescription()).append('\n');
        sb.append("结果类型: ")
                .append(
                        Optional.ofNullable(metric.getValueType())
                                .map(MetricValueType::getDescription)
                                .orElse("未配置"))
                .append('\n');
        sb.append("标准结果单位: ").append(Objects.toString(metric.getUnit(), "未配置")).append('\n');
        sb.append("结果换算: 标准结果 = 度量表达式原始结果 × ")
                .append(
                        Objects.requireNonNullElse(metric.getResultMultiplier(), BigDecimal.ONE)
                                .toPlainString())
                .append("；仅换算一次。\n");
        if (metric.getValueType() == MetricValueType.MONEY) {
            sb.append("币种: ")
                    .append(Objects.toString(metric.getCurrencyCode(), "未配置"))
                    .append("；仅汇总同币种数据，混合币种须先过滤或明确换汇规则。\n");
        }
        if (metric.getValueType() == MetricValueType.PERCENT) {
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

    public String formatNotFound(String hint, List<MetricInfoBo> suggestions) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("未找到与\"%s\"匹配的指标口径。请确认指标名称,或在指标口径管理中定义它。%n", hint));
        if (!suggestions.isEmpty()) {
            sb.append("可选的已有指标: ");
            sb.append(
                    suggestions.stream()
                            .map(m -> m.getName() + "(" + m.getMetricKey() + ")")
                            .collect(Collectors.joining(", ")));
        } else {
            sb.append("当前尚未定义任何指标口径。");
        }
        return sb.toString();
    }

    public String formatCaliberWithAlternatives(MetricInfoBo best, List<MetricInfoBo> others) {
        return convert(best)
                + String.format(
                        "%n（注意:有多个相近指标,请确认你要的是\"%s\"。其他候选: %s）%n",
                        best.getName(),
                        others.stream()
                                .map(m -> m.getName() + "(" + m.getMetricKey() + ")")
                                .collect(Collectors.joining(", ")));
    }
}

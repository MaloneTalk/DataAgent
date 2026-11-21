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
package io.github.malonetalk.agent.tools.impls;

import static io.github.malonetalk.common.SemanticConstants.METRIC_CANDIDATE_DISPLAY_LIMIT;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.agent.tools.DataAgentTool;
import io.github.malonetalk.agent.tools.RestTool;
import io.github.malonetalk.agent.tools.ToolDatasourceResolver;
import io.github.malonetalk.agent.tools.ToolExceptionMapper;
import io.github.malonetalk.agent.tools.ToolResultBoConverter;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.model.bo.ToolResultBo;
import io.github.malonetalk.model.converter.MetricConverter;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.MetricUse;
import io.github.malonetalk.service.MetricClarificationService;
import io.github.malonetalk.service.PermissionService;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GetMetricCaliberTool implements DataAgentTool {

    private final PermissionService permissions;

    private final MetricClarificationService clarification;
    private final MetricConverter metricConverter;
    private final ToolDatasourceResolver datasourceResolver;
    private final ToolExceptionMapper toolExceptionMapper;

    @RestTool
    @Tool(
            name = "get_metric_caliber",
            converter = ToolResultBoConverter.class,
            description =
                    """
                    Get the precise business caliber (definition) of a metric when you are unsure \
                    how it should be computed — which measure expression to use, which time field \
                    to apply, or whether test data should be excluded. It returns the metric's \
                    measure expression, filters, time field, calculation type, value type, unit, \
                    result multiplier, currency and caliber notes. Apply the result multiplier \
                    exactly once; percentage values are ratios and are multiplied by 100 only \
                    for display. Call this whenever \
                    the exact caliber of a mentioned metric is unclear before generating SQL. \
                    You may pass the user's question verbatim: the matcher scans the question for \
                    known metric names and aliases. Prefer metric_key for an explicit identifier. \
                    AMBIGUOUS results never choose a definition: if requiresClarification is true, \
                    call ask_user with the candidate choices and wait for the user. Then query \
                    again using the confirmed metric_key. For multiple independent metrics, \
                    query each name separately. Never select the first candidate automatically.\
                    """)
    public ToolResultBo getMetricCaliber(
            @ToolParam(
                            name = "datasource_id",
                            description =
                                    "Datasource id. Optional only when exactly one datasource is"
                                            + " available.",
                            required = false)
                    Integer datasourceId,
            @ToolParam(
                            name = "hint",
                            description =
                                    "A metric name, or the user's question verbatim, e.g. 流水 /"
                                            + " 销售额 / 上个月的GMV是多少",
                            required = false)
                    String hint,
            @ToolParam(
                            name = "metric_key",
                            description =
                                    "Exact stable metric key within the datasource; takes"
                                            + " precedence over hint.",
                            required = false)
                    String metricKey,
            ToolCallContext ctx) {
        return toolExceptionMapper.runResult(
                () -> {
                    permissions.require(ctx.userId(), PermissionEnum.SEMANTIC_METRIC_READ);
                    var datasource = datasourceResolver.resolve(ctx, datasourceId);
                    var lookup =
                            clarification.lookup(
                                    ctx,
                                    datasource.getId(),
                                    Optional.ofNullable(metricKey),
                                    Optional.ofNullable(hint));
                    return new ToolResultBo(
                            lookup.text(),
                            Map.of(
                                    "metrics",
                                    lookup.resolvedMetric().stream()
                                            .map(
                                                    metric ->
                                                            new MetricUse(
                                                                    metric.getMetricKey(),
                                                                    metric.getName(),
                                                                    metric.getMeasureExpr(),
                                                                    metric.getFilters()))
                                            .toList(),
                                    "lookupStatus",
                                    lookup.status().name(),
                                    "requiresClarification",
                                    lookup.requiresClarification(),
                                    "candidates",
                                    lookup.candidates().stream()
                                            .limit(METRIC_CANDIDATE_DISPLAY_LIMIT)
                                            .map(metricConverter::toCandidate)
                                            .toList(),
                                    "candidateCount",
                                    lookup.candidates().size(),
                                    "datasourceId",
                                    datasource.getId(),
                                    "datasourceName",
                                    datasource.getName()));
                });
    }
}

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
package io.github.malonetalk.agent.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.malonetalk.service.MetricService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class GetMetricCaliberTool implements MarkAgentTool {

    private final MetricService metricService;

    @Tool(
            name = "get_metric_caliber",
            description =
                    """
                    Get the precise business caliber (definition) of a metric when you are unsure \
                    how it should be computed — which measure expression to use, which time field \
                    to apply, or whether test data should be excluded. Pass a natural-language \
                    description of the metric (e.g. "流水", "销售额", "复购率"), and it returns the \
                    metric's measure expression, filters, time field and caliber notes. Call this \
                    whenever the exact caliber of a mentioned metric is unclear before generating SQL.\
                    """)
    public String getMetricCaliber(
            @ToolParam(
                            name = "hint",
                            description =
                                    "Natural-language description of the metric, e.g. 流水 / 销售额 /"
                                            + " 复购率")
                    String hint) {
        try {
            return metricService.getCaliberByHint(hint);
        } catch (Exception e) {
            log.error("Failed to get metric caliber: " + hint, e);
            return "Failed to get metric caliber: " + e.getMessage();
        }
    }
}

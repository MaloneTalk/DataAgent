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

import io.agentscope.core.memory.Memory;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.agent.tools.DataAgentTool;
import io.github.malonetalk.agent.tools.ToolExceptionMapper;
import io.github.malonetalk.agent.tools.ToolResultBoConverter;
import io.github.malonetalk.model.bo.ChartPayloadBo;
import io.github.malonetalk.model.bo.ChartSpecBo;
import io.github.malonetalk.model.bo.ToolResultBo;
import io.github.malonetalk.model.converter.ChartConverter;
import io.github.malonetalk.service.ChartService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GenerateChartTool implements DataAgentTool {
    private final ChartService chartService;
    private final ChartConverter converter;
    private final ToolExceptionMapper toolExceptionMapper;

    @Tool(
            name = "generate_chart",
            converter = ToolResultBoConverter.class,
            description =
                    """
                    根据本会话 execute_sql 的真实查询结果生成交互图表，图表会直接展示在聊天中。
                    必须先执行 SQL 并获得 Result ID，再调用本工具；只填写图表类型和字段映射，不传数据或代码。
                    SQL 应先完成聚合和 ORDER BY；截断、空结果或重复维度需要先调整 SQL。
                    支持 bar、line、pie、scatter。折线图和柱状图支持多个指标及可选分组。
                    饼图仅支持一个非负指标且不分组；散点图要求横纵轴均为数值。
                    改图时可复用同一个 Result ID。成功后无需重复输出图表配置或数据。
                    """)
    public ToolResultBo generateChart(
            @ToolParam(name = "result_id", description = "Result ID returned by execute_sql")
                    String resultId,
            @ToolParam(name = "chart_type", description = "bar, line, pie or scatter")
                    String chartType,
            @ToolParam(name = "title", description = "Short chart title") String title,
            @ToolParam(
                            name = "x_field",
                            description = "Query column for the category or scatter x axis")
                    String xField,
            @ToolParam(name = "y_fields", description = "Numeric query columns for measures")
                    List<String> yFields,
            @ToolParam(
                            name = "series_field",
                            description = "Optional query column for grouping",
                            required = false)
                    String seriesField,
            Memory memory,
            ToolCallContext context) {
        return toolExceptionMapper.runResult(
                () -> {
                    var spec =
                            ChartSpecBo.builder()
                                    .chartType(chartType)
                                    .title(title)
                                    .xField(xField)
                                    .yFields(yFields)
                                    .seriesField(Optional.ofNullable(seriesField).orElse(""))
                                    .build();
                    var chart =
                            chartService.generate(resultId, spec, memory.getMessages(), context);
                    return new ToolResultBo(
                            "Chart generated: " + title,
                            Map.of(ChartPayloadBo.METADATA_KEY, converter.toMetadata(chart)));
                });
    }
}

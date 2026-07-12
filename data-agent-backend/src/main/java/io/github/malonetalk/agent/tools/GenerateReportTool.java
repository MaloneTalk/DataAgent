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
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.service.ReportService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@AllArgsConstructor
public class GenerateReportTool implements MarkAgentTool {

    private static final String SUCCESS = "SUCCESS";
    private static final String FAIL = "FAIL";
    private final ReportService reportService;

    private static final String DEFAULT_SESSION = "__default__";

    @Tool(
            name = ToolNameConstants.GENERATE_REPORT,
            description =
                    """
                    保存数据分析报告。
                    只有当前面所有分析步骤均成功完成时，才调用本工具保存报告。如果前面的分析尚未完成或出现错误，
                    请先解决分析过程中的问题，而非直接调用本工具。
                    报告正文需基于用户问题、SQL 查询结果和分析过程，生成结构化、逻辑清晰的 Markdown 文档。
                    需要展示图表时使用 ```echarts 代码块，内容为纯 JSON 的 ECharts Option 配置。

                    返回值：工具返回 "SUCCESS" 表示保存成功，返回 "FAIL: <原因>" 表示保存失败及失败原因。
                    当保存失败时，请尽可能重试（有限次），尝试恢复。如果失败原因无法通过有限次重试解决，
                    则将失败原因告知用户，并直接将完整的报告内容输出给用户。
                    当保存成功时，无需将报告内容复述给用户，直接告知用户"报告生成成功，请查看"即可。
                    """)
    public String generateReport(
            @ToolParam(
                            name = "title",
                            description =
                                    """
                                    报告标题，应简洁准确地概括本次分析的主题和核心结论
                                    """)
                    String title,
            @ToolParam(
                            name = "markdownText",
                            description =
                                    """
                                    完整的 Markdown 格式报告正文。
                                    必须使用 Markdown 语法，禁止 HTML 或内联脚本。
                                    标题与段落清晰分层，合理使用列表、表格和代码块，关注可读性与信息密度。
                                    图表使用 ```echarts 代码块，内容为纯 JSON 的 ECharts Option 配置，例如：
                                    {
                                        "title": { "text": "月度销售额" },
                                        "tooltip": { "trigger": "axis" },
                                        "xAxis": { "type": "category", "data": ["1月", "2月"] },
                                        "yAxis": { "type": "value" },
                                        "series": [{ "type": "bar", "data": [120, 200] }]
                                    }
                                    报告建议包含：执行摘要 → 分析背景与用户诉求 → 分析过程 → 结果解读与洞察 → 建议与后续行动。
                                    所有结论必须基于实际数据结果推理，不得杜撰。
                                    """)
                    String markdownText,
            ToolCallContext ctx) {
        String sessionId = ctx.sessionId();
        if (!StringUtils.hasText(sessionId)) {
            log.warn("工具上下文中不存在session_id，使用默认session存储");
            sessionId = DEFAULT_SESSION;
        }
        try {
            reportService.create(sessionId, title, markdownText);
            return SUCCESS;
        } catch (Exception e) {
            log.warn("报告保存失败", e);
            return FAIL + ": " + e.getMessage();
        }
    }
}

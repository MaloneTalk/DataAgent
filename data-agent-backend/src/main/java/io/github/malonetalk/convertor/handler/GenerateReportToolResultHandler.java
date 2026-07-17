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
package io.github.malonetalk.convertor.handler;

import io.agentscope.core.message.ToolResultBlock;
import io.github.malonetalk.agent.tools.ToolCallConstants;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.ChatStreamEvent.ToolResultInfo;
import io.github.malonetalk.dto.ReportResponse;
import io.github.malonetalk.enums.ChatStreamEventType;
import io.github.malonetalk.service.ReportService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class GenerateReportToolResultHandler implements ToolResultHandler {

    private final ReportService reportService;

    @Override
    public boolean supports(ToolResultBlock block, String text) {
        String t = text == null ? "" : text.replace("\"", "").trim();
        return ToolCallConstants.GENERATE_REPORT.equals(block.getName())
                && t.startsWith(ToolCallConstants.SUCCESS_PREFIX);
    }

    @Override
    public ChatStreamEvent handle(
            ToolResultBlock block, String text, String messageId, boolean isLast) {
        try {
            Integer reportId =
                    Integer.parseInt(
                            text.replace("\"", "")
                                    .substring(ToolCallConstants.SUCCESS_PREFIX.length()));
            ReportResponse reportResponse = reportService.findById(reportId);
            return new ChatStreamEvent(
                    ChatStreamEventType.REPORT,
                    messageId,
                    isLast,
                    text,
                    null,
                    new ToolResultInfo(
                            block.getId(),
                            block.getName(),
                            reportResponse.content(),
                            block.isSuspended()));
        } catch (Exception e) {
            log.warn("解析报表结果失败", e);
            return ToolResultHandler.defaultHandle(block, text, messageId, isLast);
        }
    }
}

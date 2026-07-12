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
package io.github.malonetalk.convertor;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.github.malonetalk.agent.tools.ToolCallConstants;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.ChatStreamEvent.ToolCallInfo;
import io.github.malonetalk.dto.ChatStreamEvent.ToolResultInfo;
import io.github.malonetalk.dto.ReportResponse;
import io.github.malonetalk.enums.ChatStreamEventType;
import io.github.malonetalk.service.ReportService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@AllArgsConstructor
public class EventConverter {

    private final ReportService reportService;

    public List<ChatStreamEvent> map(Event event) {
        Msg msg = event.getMessage();
        String messageId = msg.getId();
        boolean isLast = event.isLast();
        logEvent(event, msg, messageId, isLast);

        if (event.getType() == EventType.SUMMARY) {
            return handleSummary(msg, messageId, isLast);
        } else if (event.getType() == EventType.REASONING && isLast) {
            return handleReasoningLast(msg, messageId, isLast);
        } else {
            return handleContentBlocks(event.getType(), msg, messageId, isLast);
        }
    }

    private List<ChatStreamEvent> handleContentBlocks(
            EventType eventType, Msg msg, String messageId, boolean isLast) {
        return msg.getContent().stream()
                .map(block -> convertBlock(block, eventType, messageId, isLast))
                .filter(Objects::nonNull)
                .toList();
    }

    private void logEvent(Event event, Msg msg, String messageId, boolean isLast) {
        log.info(
                "Event received: type={}, isLast={}, msgId={}, contentBlocks={}, blockTypes={}",
                event.getType(),
                isLast,
                messageId,
                msg.getContent().size(),
                msg.getContent().stream().map(b -> b.getClass().getSimpleName()).toList());
    }

    private List<ChatStreamEvent> handleSummary(Msg msg, String messageId, boolean isLast) {
        String text = extractAllText(msg);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return List.of(
                new ChatStreamEvent(
                        ChatStreamEventType.SUMMARY, messageId, isLast, text, null, null));
    }

    private List<ChatStreamEvent> handleReasoningLast(Msg msg, String messageId, boolean isLast) {
        List<ChatStreamEvent> results = new ArrayList<>();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ToolUseBlock tub) {
                results.add(
                        new ChatStreamEvent(
                                ChatStreamEventType.TOOL_CALL,
                                messageId,
                                isLast,
                                null,
                                new ToolCallInfo(tub.getId(), tub.getName(), tub.getInput()),
                                null));
            }
        }
        if (results.isEmpty()) {
            results.add(
                    new ChatStreamEvent(
                            ChatStreamEventType.TEXT, messageId, isLast, null, null, null));
        }
        return Collections.unmodifiableList(results);
    }

    private ChatStreamEvent convertBlock(
            ContentBlock block, EventType eventType, String messageId, boolean isLast) {
        if (block instanceof ThinkingBlock tb) {
            return convertThinking(tb, messageId, isLast);
        } else if (block instanceof ToolUseBlock tub && eventType != EventType.REASONING) {
            return convertToolUse(tub, messageId, isLast);
        } else if (block instanceof ToolResultBlock trb) {
            return convertToolResult(trb, messageId, isLast);
        } else if (block instanceof TextBlock tb) {
            return convertText(tb, messageId, isLast);
        } else {
            log.warn("Unknown ContentBlock type: {}", block.getClass().getName());
            return null;
        }
    }

    private ChatStreamEvent convertThinking(ThinkingBlock tb, String messageId, boolean isLast) {
        String thinking = tb.getThinking();
        if (!StringUtils.hasText(thinking)) {
            return null;
        }
        return new ChatStreamEvent(
                ChatStreamEventType.THINKING, messageId, isLast, thinking, null, null);
    }

    private ChatStreamEvent convertToolUse(ToolUseBlock tub, String messageId, boolean isLast) {
        // REASONING events already emit tool calls in the isLast branch above with
        // complete input. Skip incremental duplicates here to avoid emitting partial
        // (empty) tool arguments that the model hasn't finished generating yet.
        return new ChatStreamEvent(
                ChatStreamEventType.TOOL_CALL,
                messageId,
                isLast,
                null,
                new ToolCallInfo(tub.getId(), tub.getName(), tub.getInput()),
                null);
    }

    private ChatStreamEvent convertToolResult(
            ToolResultBlock trb, String messageId, boolean isLast) {
        String text = extractOutputText(trb);
        // TODO：可能需要重构为策略模式
        if (trb.isSuspended() && ToolCallConstants.ASK_USER.equals(trb.getName())) {
            return new ChatStreamEvent(
                    ChatStreamEventType.QUESTION,
                    messageId,
                    isLast,
                    text,
                    new ToolCallInfo(trb.getId(), trb.getName(), Map.of()),
                    null);
        } else if (ToolCallConstants.GENERATE_REPORT.equals(trb.getName())
                && text != null
                && text.startsWith("\"" + ToolCallConstants.SUCCESS + " ")) {
            Integer reportId =
                    Integer.parseInt(
                            text.replace("\"", "")
                                    .substring(ToolCallConstants.SUCCESS.length() + 1));
            ReportResponse reportResponse = reportService.findById(reportId);
            return new ChatStreamEvent(
                    ChatStreamEventType.REPORT,
                    messageId,
                    isLast,
                    text,
                    null,
                    new ToolResultInfo(
                            trb.getId(),
                            trb.getName(),
                            reportResponse.content(),
                            trb.isSuspended()));
        } else {
            return new ChatStreamEvent(
                    ChatStreamEventType.TOOL_RESULT,
                    messageId,
                    isLast,
                    null,
                    null,
                    new ToolResultInfo(trb.getId(), trb.getName(), text, trb.isSuspended()));
        }
    }

    private ChatStreamEvent convertText(TextBlock tb, String messageId, boolean isLast) {
        String text = tb.getText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return new ChatStreamEvent(ChatStreamEventType.TEXT, messageId, isLast, text, null, null);
    }

    private String extractAllText(Msg msg) {
        return msg.getContent().stream()
                .map(
                        block -> {
                            if (block instanceof TextBlock tb) {
                                return tb.getText();
                            } else if (block instanceof ThinkingBlock tb) {
                                return tb.getThinking();
                            } else {
                                return null;
                            }
                        })
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
    }

    private String extractOutputText(ToolResultBlock trb) {
        return trb.getOutput().stream()
                .filter(block -> block instanceof TextBlock)
                .map(block -> (TextBlock) block)
                .map(TextBlock::getText)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
    }
}

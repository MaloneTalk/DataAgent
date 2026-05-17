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
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.ChatStreamEvent.ToolCallInfo;
import io.github.malonetalk.dto.ChatStreamEvent.ToolResultInfo;
import io.github.malonetalk.enums.ChatStreamEventType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventConverter {

    public static List<ChatStreamEvent> map(Event event) {
        Msg msg = event.getMessage();
        String messageId = msg.getId();
        boolean isLast = event.isLast();

        log.info(
                "Event received: type={}, isLast={}, msgId={}, contentBlocks={}, blockTypes={}",
                event.getType(),
                isLast,
                messageId,
                msg.getContent().size(),
                msg.getContent().stream().map(b -> b.getClass().getSimpleName()).toList());

        List<ChatStreamEvent> results = new ArrayList<>();

        if (event.getType() == EventType.SUMMARY) {
            String text = extractAllText(msg);
            if (text != null && !text.isEmpty()) {
                results.add(
                        new ChatStreamEvent(
                                ChatStreamEventType.SUMMARY, messageId, isLast, text, null, null));
            }
            return results;
        }

        if (event.getType() == EventType.REASONING && isLast) {
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
            return results;
        }

        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ThinkingBlock tb) {
                String thinking = tb.getThinking();
                if (thinking != null && !thinking.isEmpty()) {
                    results.add(
                            new ChatStreamEvent(
                                    ChatStreamEventType.THINKING,
                                    messageId,
                                    isLast,
                                    thinking,
                                    null,
                                    null));
                }
            } else if (block instanceof ToolUseBlock tub) {
                results.add(
                        new ChatStreamEvent(
                                ChatStreamEventType.TOOL_CALL,
                                messageId,
                                isLast,
                                null,
                                new ToolCallInfo(tub.getId(), tub.getName(), tub.getInput()),
                                null));
            } else if (block instanceof ToolResultBlock trb) {
                String outputText = extractOutputText(trb);
                results.add(
                        new ChatStreamEvent(
                                ChatStreamEventType.TOOL_RESULT,
                                messageId,
                                isLast,
                                null,
                                null,
                                new ToolResultInfo(trb.getId(), trb.getName(), outputText)));
            } else if (block instanceof TextBlock tb) {
                String text = tb.getText();
                if (text != null && !text.isEmpty()) {
                    results.add(
                            new ChatStreamEvent(
                                    ChatStreamEventType.TEXT, messageId, isLast, text, null, null));
                }
            } else {
                log.warn("Unknown ContentBlock type: {}", block.getClass().getName());
            }
        }

        return results;
    }

    private static String extractAllText(Msg msg) {
        return msg.getContent().stream()
                .filter(block -> block instanceof TextBlock || block instanceof ThinkingBlock)
                .map(
                        block -> {
                            if (block instanceof TextBlock tb) {
                                return tb.getText();
                            } else if (block instanceof ThinkingBlock tb) {
                                return tb.getThinking();
                            }
                            return "";
                        })
                .filter(text -> text != null && !text.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private static String extractOutputText(ToolResultBlock trb) {
        return trb.getOutput().stream()
                .filter(block -> block instanceof TextBlock)
                .map(block -> ((TextBlock) block).getText())
                .filter(text -> text != null && !text.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private EventConverter() {}
}

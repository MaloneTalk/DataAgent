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

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.github.malonetalk.convertor.handler.ToolResultHandler;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.TurnItem;
import io.github.malonetalk.enums.ChatStreamEventType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SessionHistoryConverter {

    public List<TurnItem> convert(List<Msg> messages) {
        List<TurnItem> turns = new ArrayList<>();
        List<ContentBlock> agentBlocks = null;

        for (Msg msg : messages) {
            boolean isUser = msg.getRole() == MsgRole.USER;
            if (isUser) {
                if (agentBlocks != null) {
                    turns.add(buildAgentTurn(agentBlocks));
                    agentBlocks = null;
                }
                String text =
                        msg.getContent().stream()
                                .filter(block -> block instanceof TextBlock)
                                .map(block -> ((TextBlock) block).getText())
                                .filter(t -> t != null && !t.isEmpty())
                                .collect(Collectors.joining());
                turns.add(new TurnItem(MsgRole.USER.name(), text, List.of()));
            } else {
                if (agentBlocks == null) {
                    agentBlocks = new ArrayList<>();
                }
                agentBlocks.addAll(msg.getContent());
            }
        }
        if (agentBlocks != null) {
            turns.add(buildAgentTurn(agentBlocks));
        }

        return turns;
    }

    private TurnItem buildAgentTurn(List<ContentBlock> blocks) {
        StringBuilder content = new StringBuilder();
        List<ChatStreamEvent> traceSteps = new ArrayList<>();

        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock tb) {
                appendText(content, tb);
            } else if (block instanceof ThinkingBlock tb) {
                appendThinking(traceSteps, tb);
            } else if (block instanceof ToolUseBlock tub) {
                appendToolCall(traceSteps, tub);
            } else if (block instanceof ToolResultBlock trb) {
                appendToolResult(traceSteps, trb);
            }
        }

        return new TurnItem(MsgRole.ASSISTANT.name(), content.toString(), traceSteps);
    }

    private void appendText(StringBuilder content, TextBlock tb) {
        String text = tb.getText();
        if (text != null && !text.isEmpty()) {
            content.append(text);
        }
    }

    private void appendThinking(List<ChatStreamEvent> traceSteps, ThinkingBlock tb) {
        String thinking = tb.getThinking();
        if (thinking == null || thinking.isEmpty()) {
            return;
        }
        int lastIdx = traceSteps.size() - 1;
        if (lastIdx >= 0 && traceSteps.get(lastIdx).type() == ChatStreamEventType.THINKING) {
            String merged =
                    (traceSteps.get(lastIdx).content() != null
                                    ? traceSteps.get(lastIdx).content()
                                    : "")
                            + thinking;
            traceSteps.set(lastIdx, thinkingEvent(merged));
        } else {
            traceSteps.add(thinkingEvent(thinking));
        }
    }

    private void appendToolCall(List<ChatStreamEvent> traceSteps, ToolUseBlock tub) {
        traceSteps.add(
                ChatStreamEvent.builder()
                        .type(ChatStreamEventType.TOOL_CALL)
                        .toolCall(
                                new ChatStreamEvent.ToolCallInfo(
                                        tub.getId(), tub.getName(), tub.getInput()))
                        .build());
    }

    private void appendToolResult(List<ChatStreamEvent> traceSteps, ToolResultBlock trb) {
        String outputText =
                trb.getOutput().stream()
                        .filter(b -> b instanceof TextBlock)
                        .map(b -> ((TextBlock) b).getText())
                        .filter(t -> t != null && !t.isEmpty())
                        .collect(Collectors.joining("\n"));
        traceSteps.add(ToolResultHandler.defaultHandle(trb, outputText, null, false));
    }

    private static ChatStreamEvent thinkingEvent(String thinking) {
        return ChatStreamEvent.builder()
                .type(ChatStreamEventType.THINKING)
                .content(thinking)
                .build();
    }
}

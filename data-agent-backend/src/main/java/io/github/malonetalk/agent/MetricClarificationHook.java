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
package io.github.malonetalk.agent;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.github.malonetalk.agent.tools.ToolCallConstants;
import io.github.malonetalk.service.MetricClarificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/** Binds metric questions before the same message is emitted, saved and executed. */
@RequiredArgsConstructor
final class MetricClarificationHook implements Hook {
    private final ToolCallContext context;
    private final MetricClarificationService clarification;

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent result) {
            Msg message = result.getReasoningMessage();
            if (message == null) return Mono.just(event);
            List<ContentBlock> content = message.getContent().stream().map(this::prepare).toList();
            result.setReasoningMessage(
                    Msg.builder()
                            .id(message.getId())
                            .name(message.getName())
                            .role(message.getRole())
                            .content(content)
                            .metadata(message.getMetadata())
                            .timestamp(message.getTimestamp())
                            .build());
        }
        return Mono.just(event);
    }

    private ContentBlock prepare(ContentBlock block) {
        if (!(block instanceof ToolUseBlock tool)
                || !ToolCallConstants.ASK_USER.equals(tool.getName())) return block;
        return clarification
                .prepareQuestion(context, tool.getId())
                .<ContentBlock>map(
                        question ->
                                ToolUseBlock.builder()
                                        .id(tool.getId())
                                        .name(tool.getName())
                                        .input(Map.of("question", question))
                                        .metadata(tool.getMetadata())
                                        .build())
                .orElse(block);
    }
}

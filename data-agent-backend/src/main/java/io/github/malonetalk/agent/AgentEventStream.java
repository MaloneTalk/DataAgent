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

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.hook.ActingChunkEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PostSummaryEvent;
import io.agentscope.core.hook.ReasoningChunkEvent;
import io.agentscope.core.hook.SummaryChunkEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Keeps the actual Agent call in the event stream's cancellation chain. */
final class AgentEventStream implements Hook {
    private final Sinks.Many<Event> events = Sinks.many().unicast().onBackpressureBuffer();

    Flux<Event> stream(ReActAgent agent, Msg message) {
        // AgentScope 1.0.11's stream() independently subscribes to call(), losing cancellation.
        return Flux.merge(
                        events.asFlux(),
                        agent.call(message)
                                .doOnSuccess(result -> complete())
                                .thenMany(Flux.<Event>empty()))
                .doOnCancel(agent::interrupt);
    }

    @Override
    public synchronized <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof ReasoningChunkEvent chunk) {
            emit(EventType.REASONING, chunk.getIncrementalChunk(), false);
        } else if (event instanceof PostReasoningEvent result) {
            emit(EventType.REASONING, result.getReasoningMessage(), true);
        } else if (event instanceof ActingChunkEvent chunk) {
            emitToolResult(chunk.getChunk(), false);
        } else if (event instanceof PostActingEvent result) {
            emitToolResult(result.getToolResult(), true);
        } else if (event instanceof SummaryChunkEvent chunk) {
            emit(EventType.SUMMARY, chunk.getIncrementalChunk(), false);
        } else if (event instanceof PostSummaryEvent result) {
            emit(EventType.SUMMARY, result.getSummaryMessage(), true);
        }
        return Mono.just(event);
    }

    private void emitToolResult(ToolResultBlock result, boolean last) {
        emit(
                EventType.TOOL_RESULT,
                Msg.builder().name("system").role(MsgRole.TOOL).content(result).build(),
                last);
    }

    private void emit(EventType type, Msg message, boolean last) {
        events.tryEmitNext(new Event(type, message, last));
    }

    private synchronized void complete() {
        events.tryEmitComplete();
    }
}

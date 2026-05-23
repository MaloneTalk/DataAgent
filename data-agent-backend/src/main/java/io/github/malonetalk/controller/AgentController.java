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
package io.github.malonetalk.controller;

import io.agentscope.core.message.Msg;
import io.github.malonetalk.agent.AgentService;
import io.github.malonetalk.agent.SessionService;
import io.github.malonetalk.common.Result;
import io.github.malonetalk.dto.ChatRequest;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.SessionInfo;
import io.github.malonetalk.dto.TurnItem;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@AllArgsConstructor
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final SessionService sessionService;

    @PostMapping("/chat")
    public Result<String> chat(@Valid @RequestBody ChatRequest request) {
        String response = agentService.chat(request.sessionId(), request.message());
        return Result.success(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> chatStream(
            @Valid @RequestBody ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "default";
        }

        String message = request.message();
        if (message == null) {
            message = "";
        }

        return agentService
                .chatStream(sessionId, message)
                .map(
                        event ->
                                ServerSentEvent.<ChatStreamEvent>builder()
                                        .event(event.type().getCode())
                                        .data(event)
                                        .build());
    }

    @GetMapping("/session/{sessionId}/debug")
    public Result<List<Msg>> getSessionDebug(@PathVariable String sessionId) {
        List<Msg> messages = sessionService.getSessionDebug(sessionId);
        return Result.success(messages);
    }

    @GetMapping("/session/{sessionId}/history")
    public Result<List<TurnItem>> getSessionHistory(@PathVariable String sessionId) {
        List<TurnItem> history = sessionService.getSessionHistory(sessionId);
        return Result.success(history);
    }

    @DeleteMapping("/session/{sessionId}")
    public Result<Boolean> clearSession(@PathVariable String sessionId) {
        sessionService.clearSession(sessionId);
        return Result.success(true);
    }

    @GetMapping("/sessions")
    public Result<List<SessionInfo>> listSessions() {
        List<SessionInfo> sessions = sessionService.listSessions();
        return Result.success(sessions);
    }

    @DeleteMapping("/session")
    public Result<Boolean> clearAllSessions() {
        sessionService.clearAllSessions();
        return Result.success(true);
    }
}

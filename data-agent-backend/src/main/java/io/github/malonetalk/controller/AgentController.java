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

import static io.github.malonetalk.common.Constants.ADMIN_ROLE_ID;

import io.agentscope.core.message.Msg;
import io.github.malonetalk.agent.AgentService;
import io.github.malonetalk.agent.SessionService;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.Result;
import io.github.malonetalk.common.UserContext;
import io.github.malonetalk.dto.ChatRequest;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.SessionInfo;
import io.github.malonetalk.dto.TurnItem;
import io.github.malonetalk.exception.BusinessException;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final SessionService sessionService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> chatStream(
            @Valid @RequestBody ChatRequest request) {
        int userId = UserContext.require().userId();
        log.info("SSE chat stream started: sessionId={}, userId={}", request.sessionId(), userId);
        ToolCallContext context =
                ToolCallContext.builder()
                        .sessionId(request.sessionId())
                        .userInput(request.message())
                        .datasourceId(request.datasourceId())
                        .userId(userId)
                        .build();
        return agentService
                .chatStream(context, request.toolResults())
                .map(
                        event ->
                                ServerSentEvent.<ChatStreamEvent>builder()
                                        .event(event.type().getCode())
                                        .data(event)
                                        .build());
    }

    @GetMapping("/session/{sessionId}/debug")
    public Result<List<Msg>> getSessionDebug(@PathVariable String sessionId) {
        Integer userId = resolveUserId();
        List<Msg> messages = sessionService.getSessionDebug(sessionId, userId);
        return Result.success(messages);
    }

    @GetMapping("/session/{sessionId}/history")
    public Result<List<TurnItem>> getSessionHistory(@PathVariable String sessionId) {
        Integer userId = resolveUserId();
        List<TurnItem> history = sessionService.getSessionHistory(sessionId, userId);
        return Result.success(history);
    }

    @DeleteMapping("/session/{sessionId}")
    public Result<Boolean> clearSession(@PathVariable String sessionId) {
        Integer userId = resolveUserId();
        sessionService.clearSession(sessionId, userId);
        return Result.success(true);
    }

    @GetMapping("/sessions")
    public Result<List<SessionInfo>> listSessions() {
        Integer userId = resolveUserId();
        List<SessionInfo> sessions = sessionService.listSessions(userId);
        return Result.success(sessions);
    }

    @DeleteMapping("/session")
    public Result<Boolean> clearAllSessions() {
        UserContext user = UserContext.require();
        if (user.roleId() == null || user.roleId() != ADMIN_ROLE_ID) {
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        sessionService.clearAllSessions(null);
        return Result.success(true);
    }

    /**
     * 解析当前用户的 userId：admin 返回 null（不过滤），普通用户返回 userId。
     *
     * <p>admin (role_id=1) 能看到所有 session 且跳过所有权检查。
     */
    private static Integer resolveUserId() {
        UserContext user = UserContext.require();
        if (user.roleId() != null && user.roleId() == ADMIN_ROLE_ID) {
            return null;
        }
        return user.userId();
    }
}

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
import io.github.malonetalk.agent.provenance.AnswerProvenanceService;
import io.github.malonetalk.annotation.RequirePermission;
import io.github.malonetalk.common.Result;
import io.github.malonetalk.dto.ChatRequest;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.SessionInfo;
import io.github.malonetalk.dto.TurnItem;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.model.dto.DataEvidencePageQuery;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.DataEvidenceVo;
import io.github.malonetalk.service.PermissionService;
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
    private final PermissionService permissions;
    private final SessionService sessionService;
    private final UserContextHolder userContextHolder;
    private final AnswerProvenanceService answerProvenanceService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission(PermissionEnum.ANALYSIS_CHAT_USE)
    public Flux<ServerSentEvent<ChatStreamEvent>> chatStream(
            @Valid @RequestBody ChatRequest request) {
        // Read userId before Reactor switches threads.
        int userId = userContextHolder.checkAndGet().userId();
        log.info("SSE chat stream started: sessionId={}, userId={}", request.sessionId(), userId);
        return agentService
                .chatStream(
                        userId,
                        request.sessionId(),
                        request.message(),
                        request.toolResults(),
                        request.datasourceId())
                .map(
                        event ->
                                ServerSentEvent.<ChatStreamEvent>builder()
                                        .event(event.type().getCode())
                                        .data(event)
                                        .build());
    }

    @GetMapping("/session/{sessionId}/debug")
    @RequirePermission(PermissionEnum.ANALYSIS_SESSION_DEBUG)
    public Result<List<Msg>> getSessionDebug(@PathVariable String sessionId) {
        Integer userId = readScopedUserId();
        List<Msg> messages = sessionService.getSessionDebug(sessionId, userId);
        return Result.success(messages);
    }

    @GetMapping("/session/{sessionId}/history")
    @RequirePermission(PermissionEnum.ANALYSIS_SESSION_READ)
    public Result<List<TurnItem>> getSessionHistory(@PathVariable String sessionId) {
        Integer userId = readScopedUserId();
        List<TurnItem> history = sessionService.getSessionHistory(sessionId, userId);
        return Result.success(history);
    }

    @GetMapping("/evidence")
    @RequirePermission(PermissionEnum.ASSET_EVIDENCE_READ)
    public Result<PageResponse<DataEvidenceVo>> listDataEvidence(
            @Valid DataEvidencePageQuery query) {
        return Result.success(answerProvenanceService.getPage(query, readScopedUserId()));
    }

    @DeleteMapping("/session/{sessionId}")
    @RequirePermission(PermissionEnum.ANALYSIS_SESSION_DELETE)
    public Result<Boolean> clearSession(@PathVariable String sessionId) {
        Integer userId = userContextHolder.requireScopedUserId();
        sessionService.clearSession(sessionId, userId);
        return Result.success(true);
    }

    @GetMapping("/sessions")
    @RequirePermission(PermissionEnum.ANALYSIS_SESSION_READ)
    public Result<List<SessionInfo>> listSessions() {
        Integer userId = readScopedUserId();
        List<SessionInfo> sessions = sessionService.listSessions(userId);
        return Result.success(sessions);
    }

    @DeleteMapping("/session")
    @RequirePermission(PermissionEnum.ANALYSIS_SESSION_DELETE_ALL)
    public Result<Boolean> clearAllSessions() {
        sessionService.clearAllSessions(null);
        return Result.success(true);
    }

    private Integer readScopedUserId() {
        var user = userContextHolder.checkAndGet();
        return permissions.has(user, PermissionEnum.ANALYSIS_SESSION_READ_ALL)
                ? null
                : user.userId();
    }
}

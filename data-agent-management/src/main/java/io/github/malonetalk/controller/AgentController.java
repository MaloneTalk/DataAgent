package io.github.malonetalk.controller;

import io.github.malonetalk.agent.AgentService;
import io.github.malonetalk.common.Result;
import io.github.malonetalk.dto.ChatRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public Result<String> chat(@Valid @RequestBody ChatRequest request) {
        String response = agentService.chat(request.sessionId(), request.message());
        return Result.success(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "default";
        }

        String message = request.message();
        if (message == null) {
            message = "";
        }

        return agentService.chatStream(sessionId, message);
    }

    @DeleteMapping("/session/{sessionId}")
    public Result<Boolean> clearSession(@PathVariable String sessionId) {
        agentService.clearSession(sessionId);
        return Result.success(true);
    }

    @DeleteMapping("/session")
    public Result<Boolean> clearAllSessions() {
        agentService.clearAllSessions();
        return Result.success(true);
    }
}

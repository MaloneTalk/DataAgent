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
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.session.Session;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.github.malonetalk.agent.models.ModelFactory;
import io.github.malonetalk.agent.models.ModelProperties;
import io.github.malonetalk.agent.provenance.AnswerProvenanceService;
import io.github.malonetalk.agent.skill.SkillLoaderService;
import io.github.malonetalk.agent.tools.MarkAgentTool;
import io.github.malonetalk.agent.tools.ToolCallConstants;
import io.github.malonetalk.convertor.EventConverter;
import io.github.malonetalk.dto.ChatRequest;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.enums.ChatStreamEventType;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.exception.ErrorResponse;
import io.github.malonetalk.exception.ExceptionResponseMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.PermissionService;
import io.github.malonetalk.web.TraceIdFilter;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final ModelFactory modelFactory;
    private final List<MarkAgentTool> allToolBeans;
    private final ModelProperties modelProperties;
    private final SessionService sessionService;
    private final SkillLoaderService skillLoaderService;
    private final ExceptionResponseMapper exceptionResponseMapper;
    private final EventConverter eventConverter;
    private final DatasourceService datasourceService;
    private final AnswerProvenanceService answerProvenanceService;
    private final PermissionService permissions;
    private final DataPermissionService dataPermissions;
    private Toolkit toolkit;
    private SkillBox skillBox;

    @PostConstruct
    public void init() {
        this.toolkit = new Toolkit();
        allToolBeans.forEach(this.toolkit::registerTool);
        this.skillBox = skillLoaderService.createSkillBox(toolkit);
    }

    public Flux<ChatStreamEvent> chatStream(
            int userId,
            String sessionId,
            String userInput,
            List<ChatRequest.ToolResultInput> toolResults,
            Integer datasourceId) {
        return Flux.defer(
                        () -> streamAgent(userId, sessionId, userInput, toolResults, datasourceId))
                .onErrorResume(this::toErrorEvent);
    }

    private Flux<ChatStreamEvent> streamAgent(
            int userId,
            String sessionId,
            String userInput,
            List<ChatRequest.ToolResultInput> toolResults,
            Integer datasourceId) {
        var user = permissions.require(userId, PermissionEnum.ANALYSIS_CHAT_USE);
        // Verify unique ownership before loading memory or binding a datasource.
        sessionService.bindUserSession(userId, sessionId);
        authorizeDatasource(user, sessionId, datasourceId);
        ReActAgent agent =
                createAgent(ToolCallContext.builder().sessionId(sessionId).userId(userId).build());

        Session session = sessionService.getSession();
        agent.loadIfExists(session, sessionId);
        validateClientToolResults(userId, sessionId, toolResults);
        Msg userMsg = buildUserMessage(userInput, toolResults);
        String answerId =
                toolResults == null || toolResults.isEmpty()
                        ? userMsg.getId()
                        : sessionService.getLatestUserMessageId(sessionId).orElse(userMsg.getId());
        List<ChatStreamEvent> provenanceEvents = new ArrayList<>();

        StreamOptions streamOptions =
                StreamOptions.builder()
                        .eventTypes(EventType.REASONING, EventType.TOOL_RESULT, EventType.SUMMARY)
                        .incremental(true)
                        .includeReasoningResult(true)
                        .build();

        // traceId 从请求线程的 MDC 取出（TraceIdFilter 已设置），贴到 agent.stream 这个「源头」上：
        // doOnEach 在 agentscope 的异步发射线程上触发（早于下游 EventConverter），保证内部日志也带 traceId。
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        return agent.stream(userMsg, streamOptions)
                .doOnEach(
                        signal -> {
                            if (traceId != null && !traceId.isBlank()) {
                                MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, traceId);
                            }
                        })
                .doFinally(
                        signalType ->
                                log.info(
                                        "SSE chat stream finished: sessionId={}, signal={}",
                                        sessionId,
                                        signalType))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(eventConverter::map)
                .doOnNext(
                        event -> {
                            if (event.toolCall() != null || event.toolResult() != null)
                                provenanceEvents.add(event);
                        })
                .doOnComplete(
                        () -> saveProvenance(sessionId, answerId, provenanceEvents, "COMPLETE"))
                .doFinally(
                        signalType -> {
                            try {
                                agent.saveTo(session, sessionId);
                            } finally {
                                if (signalType != SignalType.ON_COMPLETE) {
                                    saveProvenance(
                                            sessionId, answerId, provenanceEvents, "PARTIAL");
                                }
                                MDC.remove(TraceIdFilter.TRACE_ID_MDC_KEY);
                            }
                        });
    }

    private void authorizeDatasource(UserContextBo user, String sessionId, Integer datasourceId) {
        if (datasourceId != null) {
            dataPermissions.requireDatasource(user, datasourceId);
            datasourceService.bindSessionDatasource(sessionId, datasourceId);
        }
        try {
            dataPermissions.requireDatasource(
                    user, datasourceService.getDatasourceForSession(sessionId).getId());
        } catch (BusinessException ex) {
            // Never silently switch an already bound session to another source.
            if (sessionService.hasDatasourceBinding(sessionId)) throw ex;
            var allowed =
                    datasourceService.findAll().stream()
                            .filter(ds -> Status.ACTIVE.getCode().equals(ds.getStatus()))
                            .filter(ds -> dataPermissions.canDatasource(user, ds.getId()))
                            .findFirst()
                            .orElseThrow(() -> ex);
            datasourceService.bindSessionDatasource(sessionId, allowed.getId());
        }
    }

    private void validateClientToolResults(
            int userId, String sessionId, List<ChatRequest.ToolResultInput> toolResults) {
        if (toolResults != null && !toolResults.isEmpty()) {
            var pending =
                    sessionService.getSessionDebug(sessionId, userId).stream()
                            .flatMap(msg -> msg.getContent().stream())
                            .filter(block -> block instanceof ToolUseBlock)
                            .map(block -> (ToolUseBlock) block)
                            .filter(block -> ToolCallConstants.ASK_USER.equals(block.getName()))
                            .map(ToolUseBlock::getId)
                            .collect(Collectors.toSet());
            if (toolResults.stream()
                    .anyMatch(
                            result ->
                                    !ToolCallConstants.ASK_USER.equals(result.toolName())
                                            || !pending.contains(result.toolCallId()))) {
                throw BusinessException.of(ErrorCode.FORBIDDEN);
            }
        }
    }

    private void saveProvenance(
            String sessionId, String answerId, List<ChatStreamEvent> events, String status) {
        try {
            answerProvenanceService.save(sessionId, answerId, events, status);
        } catch (RuntimeException e) {
            log.error(
                    "Failed to save answer provenance: sessionId={}, answerId={}",
                    sessionId,
                    answerId,
                    e);
        }
    }

    private Msg buildUserMessage(String userInput, List<ChatRequest.ToolResultInput> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            String text = userInput != null ? userInput : "";
            return Msg.builder().textContent(text).build();
        }
        List<ContentBlock> blocks =
                toolResults.stream().<ContentBlock>map(this::toToolResultBlock).toList();
        return Msg.builder().role(MsgRole.TOOL).content(blocks).build();
    }

    private ToolResultBlock toToolResultBlock(ChatRequest.ToolResultInput toolResult) {
        return ToolResultBlock.builder()
                .id(toolResult.toolCallId())
                .name(toolResult.toolName())
                .output(TextBlock.builder().text(toolResult.output()).build())
                .build();
    }

    private Flux<ChatStreamEvent> toErrorEvent(Throwable exception) {
        ErrorResponse errorResponse = exceptionResponseMapper.resolve(exception);
        exceptionResponseMapper.logMapped(log, exception, errorResponse);
        return Flux.just(
                ChatStreamEvent.builder()
                        .type(ChatStreamEventType.ERROR)
                        .isLast(true)
                        .content(errorResponse.message())
                        .errorCode(errorResponse.errorCode().getCode())
                        .build());
    }

    private ReActAgent createAgent(ToolCallContext toolCallContext) {
        ToolExecutionContext context =
                ToolExecutionContext.builder().register(toolCallContext).build();
        return ReActAgent.builder()
                .name("DataAgent")
                .sysPrompt(systemPrompt())
                .model(modelFactory.getInstance(modelProperties))
                .toolkit(toolkit)
                .toolExecutionContext(context)
                .skillBox(skillBox)
                .memory(new InMemoryMemory())
                .maxIters(10)
                .enablePendingToolRecovery(true)
                .build();
    }

    private String systemPrompt() {
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        return """
        你是一个数据助手，可以帮助用户查询数据库中的数据。
        当前时区是 Asia/Shanghai。
        今天是 %s，昨天是 %s，明天是 %s。
        当用户提到今天、昨天、明天、上周、本周等相对日期时，必须先按以上当前日期换算成具体日期。
        查询日期、星期、节假日、调休或日期差时，优先使用 get_date_info 工具。
        """
                .formatted(today, today.minusDays(1), today.plusDays(1));
    }
}

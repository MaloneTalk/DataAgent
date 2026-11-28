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
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.github.malonetalk.agent.models.ModelFactory;
import io.github.malonetalk.agent.models.ModelProperties;
import io.github.malonetalk.agent.provenance.AnswerProvenanceService;
import io.github.malonetalk.agent.skill.SkillLoaderService;
import io.github.malonetalk.agent.tools.DataAgentTool;
import io.github.malonetalk.agent.tools.ToolCallConstants;
import io.github.malonetalk.convertor.EventConverter;
import io.github.malonetalk.dto.ChatRequest;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.enums.ChatStreamEventType;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.exception.ErrorResponse;
import io.github.malonetalk.exception.ExceptionResponseMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.ChatExecutionService;
import io.github.malonetalk.service.ChatExecutionService.Execution;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.MetricClarificationService;
import io.github.malonetalk.service.PermissionService;
import io.github.malonetalk.service.SessionDatasourceService;
import io.github.malonetalk.service.ToolRiskService;
import io.github.malonetalk.utils.DateTimeUtils;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ModelFactory modelFactory;
    private final List<DataAgentTool> allToolBeans;
    private final ModelProperties modelProperties;
    private final SessionService sessionService;
    private final SessionDatasourceService sessionDatasourceService;
    private final SkillLoaderService skillLoaderService;
    private final ExceptionResponseMapper exceptionResponseMapper;
    private final EventConverter eventConverter;
    private final DatasourceService datasourceService;
    private final AnswerProvenanceService answerProvenanceService;
    private final PermissionService permissions;
    private final DataPermissionService dataPermissions;
    private final MetricClarificationService metricClarification;
    private final ChatExecutionService chatExecutions;
    private final ToolRiskService toolRiskService;
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
            List<Integer> datasourceIds,
            Optional<String> executionId) {
        Context otelContext = Context.current();
        return Flux.usingWhen(
                        Mono.fromSupplier(() -> chatExecutions.start(sessionId, executionId)),
                        execution ->
                                Flux.defer(
                                                () ->
                                                        streamAgent(
                                                                userId,
                                                                sessionId,
                                                                userInput,
                                                                toolResults,
                                                                datasourceIds,
                                                                execution))
                                        .takeUntilOther(execution.cancellation())
                                        .doOnCancel(execution::cancel),
                        Execution::finish)
                .contextWrite(
                        ctx ->
                                ContextPropagationOperator.storeOpenTelemetryContext(
                                        ctx, otelContext))
                .onErrorResume(this::toErrorEvent)
                .concatWithValues(
                        ChatStreamEvent.builder()
                                .type(ChatStreamEventType.DONE)
                                .isLast(true)
                                .build());
    }

    private Flux<ChatStreamEvent> streamAgent(
            int userId,
            String sessionId,
            String userInput,
            List<ChatRequest.ToolResultInput> toolResults,
            List<Integer> datasourceIds,
            Execution execution) {
        var user = permissions.require(userId, PermissionEnum.ANALYSIS_CHAT_USE);
        // Verify unique ownership before loading memory or binding a datasource.
        sessionService.requireOwnership(userId, sessionId);
        boolean resuming = toolResults != null && !toolResults.isEmpty();
        List<Integer> requestedDatasourceIds =
                resuming ? sessionDatasourceService.findDatasourceIds(sessionId) : datasourceIds;
        if (resuming && requestedDatasourceIds.isEmpty())
            throw BusinessException.of(ErrorCode.BOUND_DATASOURCE_UNAVAILABLE);
        List<Integer> allowedDatasourceIds = resolveDatasourceIds(user, requestedDatasourceIds);
        if (!resuming)
            sessionDatasourceService.replaceSessionDatasources(
                    sessionId, allowedDatasourceIds, user);
        var eventStream = new AgentEventStream();
        ToolCallContext toolContext =
                ToolCallContext.builder()
                        .sessionId(sessionId)
                        .userId(userId)
                        .datasourceIds(allowedDatasourceIds)
                        .execution(Optional.of(execution))
                        .build();
        validateClientToolResults(userId, sessionId, toolResults);
        if (resuming) {
            toolResults =
                    toolResults.stream()
                            .map(result -> acceptMetricReply(toolContext, result))
                            .toList();
        }
        ReActAgent agent = createAgent(toolContext, eventStream);
        RuntimeContext runtimeContext =
                RuntimeContext.builder()
                        .userId(String.valueOf(userId))
                        .sessionId(sessionId)
                        .put(ToolCallContext.class, toolContext)
                        .build();
        Msg userMsg = buildUserMessage(userInput, toolResults);
        String answerId =
                toolResults == null || toolResults.isEmpty()
                        ? userMsg.getId()
                        : sessionService.getLatestUserMessageId(sessionId).orElse(userMsg.getId());
        List<ChatStreamEvent> provenanceEvents = new ArrayList<>();

        // trace context 已在 chatStream 种入 Reactor context，agentscope 会自动沿用同一 traceId。
        return eventStream.stream(agent, userMsg, runtimeContext)
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
                                if (signalType != SignalType.ON_COMPLETE) {
                                    saveProvenance(
                                            sessionId, answerId, provenanceEvents, "PARTIAL");
                                }
                            } finally {
                                agent.close();
                            }
                        });
    }

    private ChatRequest.ToolResultInput acceptMetricReply(
            ToolCallContext context, ChatRequest.ToolResultInput result) {
        return metricClarification
                .acceptUserReply(context, result.toolCallId(), result.output())
                .map(
                        output ->
                                new ChatRequest.ToolResultInput(
                                        result.toolCallId(), result.toolName(), output))
                .orElse(result);
    }

    private List<Integer> resolveDatasourceIds(
            UserContextBo user, List<Integer> requestedDatasourceIds) {
        Set<Integer> accessible = dataPermissions.accessibleDatasourceIds(user);
        List<Integer> requested =
                requestedDatasourceIds == null
                        ? List.of()
                        : requestedDatasourceIds.stream()
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList();

        if (requested.isEmpty()) {
            if (accessible.isEmpty()) {
                throw BusinessException.of(ErrorCode.FORBIDDEN, "No accessible datasource.");
            }
            return accessible.stream().sorted().toList();
        }

        Set<Integer> activeIds =
                datasourceService.findActiveByIds(requested).stream()
                        .map(DatasourcePo::getId)
                        .collect(Collectors.toSet());
        for (Integer datasourceId : requested) {
            if (!activeIds.contains(datasourceId)) {
                throw BusinessException.of(
                        ErrorCode.BAD_REQUEST, "Datasource unavailable: " + datasourceId);
            }
            if (!accessible.contains(datasourceId)) {
                throw BusinessException.of(
                        ErrorCode.FORBIDDEN, "Datasource access denied: " + datasourceId);
            }
        }
        return requested;
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

    private ReActAgent createAgent(ToolCallContext toolCallContext, Hook eventStream) {
        return ReActAgent.builder()
                .name("DataAgent")
                .sysPrompt(systemPrompt())
                .model(modelFactory.getInstance(modelProperties))
                .toolkit(toolkit)
                .stateStore(sessionService.getStateStore())
                // Registered business tools retain their existing authorization and risk gates.
                .permissionContext(toolPermissions())
                .middleware(new OtelTracingMiddleware())
                .hook(new MetricClarificationHook(toolCallContext, metricClarification))
                .hook(new DangerousOperationHook(toolCallContext, toolRiskService))
                .hook(eventStream)
                .skillBox(skillBox)
                .maxIters(10)
                .enablePendingToolRecovery(true)
                .build();
    }

    private PermissionContextState toolPermissions() {
        var builder = PermissionContextState.builder().mode(PermissionMode.DONT_ASK);
        toolkit.getToolNames()
                .forEach(
                        name ->
                                builder.addAllowRule(
                                        name,
                                        new PermissionRule(
                                                name,
                                                null,
                                                PermissionBehavior.ALLOW,
                                                "business-authorization")));
        return builder.build();
    }

    private String systemPrompt() {
        LocalDate today = LocalDate.now(DateTimeUtils.DEFAULT_ZONE);
        return """
        你是一个数据助手，可以帮助用户查询数据库中的数据。
        当前时区是 Asia/Shanghai。
        今天是 %s，昨天是 %s，明天是 %s。
        当用户提到今天、昨天、明天、上周、本周等相对日期时，必须先按以上当前日期换算成具体日期。
        查询日期、星期、节假日、调休或日期差时，优先使用 get_date_info 工具。
        当前会话可能允许使用多个数据源。如果需要查询数据但尚未确定数据源，
        先调用 get_datasources；调用 get_tables、get_table_schema、get_metric_caliber、
        execute_sql 或 export_table 时，如果存在多个可选数据源，必须传 datasource_id。
        不同数据源的同名表不能混用。
        指标查询优先使用已知 metric_key。get_metric_caliber 返回 AMBIGUOUS 且 requiresClarification=true 时，
        必须调用 ask_user 展示编号、key 和口径差异，等待用户选择，不能自动采用第一名。
        用户确认后必须用所选 metric_key 再查完整口径，获得 RESOLVED 后才能执行 SQL、Python、图表、报告或导出。
        requiresClarification=false 的多个独立指标应按名称分别查询，不要让用户二选一。
        用户需要图表时，先执行 SQL 获取 Result ID，再调用 generate_chart，图表数值必须来自该查询结果。
        SQL 负责聚合与排序；绘图只指定字段映射。修改图表类型时可复用本会话已有的 Result ID。
        """
                .formatted(today, today.minusDays(1), today.plusDays(1));
    }
}

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
package io.github.malonetalk.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.MetricClarificationMapper;
import io.github.malonetalk.model.bo.MetricChoiceBo;
import io.github.malonetalk.model.bo.MetricInfoBo;
import io.github.malonetalk.model.bo.MetricLookupBo;
import io.github.malonetalk.model.converter.MetricChoiceConverter;
import io.github.malonetalk.model.converter.MetricPromptConverter;
import io.github.malonetalk.model.po.MetricClarificationPo;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** A model-generated key cannot acknowledge a choice; only a client/user reply can. */
@Service
@RequiredArgsConstructor
public class MetricClarificationService {
    private final MetricClarificationMapper mapper;
    private final MetricService metrics;
    private final MetricChoiceConverter converter;
    private final MetricPromptConverter prompts;
    private final PermissionService permissions;
    private final DataPermissionService dataPermissions;

    @Transactional
    public void clearSession(String sessionId) {
        mapper.update(
                Wrappers.<MetricClarificationPo>lambdaUpdate()
                        .eq(MetricClarificationPo::getSessionId, sessionId)
                        .set(MetricClarificationPo::getCandidatesJson, "[]")
                        .set(MetricClarificationPo::getConfirmedMetricKey, "")
                        .set(MetricClarificationPo::getQuestionToolCallId, ""));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MetricLookupBo lookup(
            ToolCallContext context,
            Integer datasourceId,
            Optional<String> metricKey,
            Optional<String> hint) {
        if (metricKey.filter(value -> !value.isBlank()).isEmpty()
                && hint.filter(value -> !value.isBlank()).isEmpty()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "metric_key 和 hint 至少填写一项");
        }
        MetricClarificationPo state = lockState(context);
        if (!converter.read(state.getCandidatesJson()).isEmpty()) {
            if (!datasourceId.equals(state.getDatasourceId())) throw required();
            List<MetricInfoBo> pending = refreshPending(state);
            if (pending.isEmpty()) return noCandidates();
            if (metricKey
                    .filter(key -> key.strip().equalsIgnoreCase(state.getConfirmedMetricKey()))
                    .isEmpty()) return ambiguous(pending);
            var selected =
                    pending.stream()
                            .filter(
                                    candidate ->
                                            candidate
                                                    .getMetricKey()
                                                    .equalsIgnoreCase(
                                                            state.getConfirmedMetricKey()))
                            .findFirst();
            if (selected.isEmpty()) return ambiguous(pending);
            clear(state);
            save(state);
            return new MetricLookupBo(
                    MetricLookupBo.Status.RESOLVED,
                    prompts.convert(selected.get()),
                    List.of(selected.get()),
                    false);
        }
        var lookup = metrics.lookupCaliber(datasourceId, metricKey, hint);
        if (lookup.requiresClarification()) {
            state.setDatasourceId(datasourceId);
            state.setCandidatesJson(converter.write(converter.choices(lookup.candidates())));
            state.setConfirmedMetricKey("");
            state.setQuestionToolCallId("");
            save(state);
        }
        return lookup;
    }

    /** Replaces a pending chat question with server-owned options before it reaches the client. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<String> prepareQuestion(ToolCallContext context, String toolCallId) {
        return mapper.find(context.userId(), context.sessionId(), true)
                .filter(state -> !converter.read(state.getCandidatesJson()).isEmpty())
                .filter(state -> state.getConfirmedMetricKey().isEmpty())
                .flatMap(
                        state -> {
                            requireAccess(context, state);
                            List<MetricInfoBo> pending = refreshPending(state);
                            if (pending.isEmpty()) return Optional.empty();
                            state.setQuestionToolCallId(toolCallId);
                            save(state);
                            return Optional.of(prompts.formatChoiceQuestion(pending));
                        });
    }

    /** Only replies to the specific server-owned metric question can acknowledge a chat choice. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<String> acceptUserReply(
            ToolCallContext context, String toolCallId, String reply) {
        return mapper.find(context.userId(), context.sessionId(), true)
                .filter(
                        state ->
                                !state.getQuestionToolCallId().isEmpty()
                                        && state.getQuestionToolCallId().equals(toolCallId))
                .map(
                        state -> {
                            var selected =
                                    converter.select(
                                            converter.read(state.getCandidatesJson()), reply);
                            state.setQuestionToolCallId("");
                            save(state);
                            if (selected.isEmpty()) {
                                return "尚未确认指标，请重新调用 ask_user，要求用户回复候选编号或完整 metricKey。";
                            }
                            return confirm(state, selected.get().metricKey(), context)
                                    ? "用户已明确选择 metricKey="
                                            + selected.get().metricKey()
                                            + "。请使用此 metric_key 重新读取完整口径，获得 RESOLVED 后再继续。"
                                    : "指标候选已变化或删除，本次选择未确认。请重新查询指标并调用 ask_user。";
                        });
    }

    /** Explicit external-client acknowledgment, deliberately not exposed as an Agent tool. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean confirmChoice(ToolCallContext context, String metricKey) {
        var state =
                mapper.find(context.userId(), context.sessionId(), true)
                        .orElseThrow(MetricClarificationService::required);
        if (converter.read(state.getCandidatesJson()).stream()
                .noneMatch(
                        candidate -> candidate.metricKey().equalsIgnoreCase(metricKey.strip()))) {
            throw required();
        }
        return confirm(state, metricKey, context);
    }

    public void requireResolved(ToolCallContext context) {
        mapper.find(context.userId(), context.sessionId(), false)
                .filter(state -> !converter.read(state.getCandidatesJson()).isEmpty())
                .ifPresent(
                        state -> {
                            throw required();
                        });
    }

    private boolean confirm(
            MetricClarificationPo state, String metricKey, ToolCallContext context) {
        requireAccess(context, state);
        List<MetricChoiceBo> offered = converter.read(state.getCandidatesJson());
        List<MetricInfoBo> pending = refreshPending(state);
        // Persist the new version and let the client ask again; throwing would roll it back.
        if (pending.isEmpty() || !offered.equals(converter.choices(pending))) return false;
        state.setConfirmedMetricKey(metricKey.strip());
        state.setQuestionToolCallId("");
        save(state);
        return true;
    }

    private void requireAccess(ToolCallContext context, MetricClarificationPo state) {
        var user = permissions.require(context.userId(), PermissionEnum.SEMANTIC_METRIC_READ);
        dataPermissions.requireActiveDatasource(
                user, state.getDatasourceId(), context.datasourceIds());
    }

    private List<MetricInfoBo> refreshPending(MetricClarificationPo state) {
        List<MetricChoiceBo> stored = converter.read(state.getCandidatesJson());
        var currentById =
                metrics
                        .findByKeys(
                                state.getDatasourceId(),
                                stored.stream().map(MetricChoiceBo::metricKey).toList())
                        .stream()
                        .collect(Collectors.toMap(MetricInfoBo::getId, metric -> metric));
        List<MetricInfoBo> refreshed =
                stored.stream()
                        .map(
                                candidate ->
                                        Optional.ofNullable(currentById.get(candidate.id()))
                                                .filter(
                                                        metric ->
                                                                metric.getMetricKey()
                                                                        .equalsIgnoreCase(
                                                                                candidate
                                                                                        .metricKey())))
                        .flatMap(Optional::stream)
                        .toList();
        var choices = converter.choices(refreshed);
        if (!choices.equals(stored)) {
            state.setCandidatesJson(converter.write(choices));
            state.setConfirmedMetricKey("");
            state.setQuestionToolCallId("");
            save(state);
        }
        return refreshed;
    }

    private MetricLookupBo ambiguous(List<MetricInfoBo> candidates) {
        return new MetricLookupBo(
                MetricLookupBo.Status.AMBIGUOUS,
                prompts.formatCandidates(candidates, true),
                candidates,
                true);
    }

    private MetricLookupBo noCandidates() {
        return new MetricLookupBo(
                MetricLookupBo.Status.NOT_FOUND,
                "原待选指标均已删除，澄清状态已清空。请补充指标名称或 metric_key 重新查询。",
                List.of(),
                false);
    }

    private void clear(MetricClarificationPo state) {
        state.setCandidatesJson("[]");
        state.setConfirmedMetricKey("");
        state.setQuestionToolCallId("");
    }

    private MetricClarificationPo lockState(ToolCallContext context) {
        if (context.userId() == null
                || context.sessionId() == null
                || context.sessionId().isBlank()
                || context.sessionId().length() > 255) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "指标查询需要有效的用户和会话");
        }
        var existing = mapper.find(context.userId(), context.sessionId(), true);
        if (existing.isPresent()) return existing.get();
        MetricClarificationPo state = new MetricClarificationPo();
        state.setUserId(context.userId());
        state.setSessionId(context.sessionId());
        state.setDatasourceId(0);
        clear(state);
        state.setIsDeleted(0);
        try {
            mapper.insert(state);
            return state;
        } catch (DuplicateKeyException exception) {
            return mapper.find(context.userId(), context.sessionId(), true)
                    .orElseThrow(() -> exception);
        }
    }

    private void save(MetricClarificationPo state) {
        if (mapper.updateById(state) != 1) {
            throw BusinessException.of(ErrorCode.DATA_CONFLICT, "指标选择状态发生变化，请重试");
        }
    }

    private static BusinessException required() {
        return BusinessException.of(
                ErrorCode.METRIC_CLARIFICATION_REQUIRED,
                "请先确认当前会话的指标口径，再用 metric_key 读取完整定义；确认前不能执行数据操作");
    }
}

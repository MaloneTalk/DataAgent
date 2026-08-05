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

import io.github.malonetalk.agent.AgentService;
import io.github.malonetalk.agent.tools.ToolCallConstants;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.entity.ScheduledAgentTask;
import io.github.malonetalk.entity.ScheduledAgentTaskRun;
import io.github.malonetalk.enums.ChatStreamEventType;
import io.github.malonetalk.mapper.ScheduledAgentTaskMapper;
import io.github.malonetalk.mapper.ScheduledAgentTaskRunMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledAgentTaskRunner {

    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final String NEEDS_USER = "NEEDS_USER";
    private static final int SUMMARY_LIMIT = 2000;

    private final AgentService agentService;
    private final ScheduledAgentTaskMapper taskMapper;
    private final ScheduledAgentTaskRunMapper runMapper;
    private final ScheduledAgentScheduleCalculator scheduleCalculator;

    public void run(Integer taskId, boolean force) {
        LocalDateTime startedAt = LocalDateTime.now();
        String lockOwner = UUID.randomUUID().toString();
        if (taskMapper.lockForRun(
                        taskId, startedAt, startedAt.plus(LOCK_DURATION), lockOwner, force)
                == 0) {
            return;
        }

        ScheduledAgentTask task = taskMapper.selectById(taskId);
        String sessionId = resolveSessionId(task);
        ScheduledAgentTaskRun run = startRun(taskId, sessionId, startedAt);
        if (taskMapper.markRunStarted(taskId, lockOwner, run.getId()) == 0) {
            runMapper.finish(
                    run.getId(),
                    FAILED,
                    null,
                    null,
                    "Scheduled task lock was lost before the run started.",
                    LocalDateTime.now());
            return;
        }

        String status = SUCCESS;
        Integer reportId = null;
        String outputSummary = null;
        String errorMessage = null;
        try {
            List<ChatStreamEvent> events =
                    agentService
                            .chatStream(sessionId, buildPrompt(task), null)
                            .collectList()
                            .block();
            status = resolveStatus(events);
            reportId = extractReportId(events);
            outputSummary = limitText(extractOutput(events));
            errorMessage = limitText(extractError(events));
        } catch (Exception e) {
            status = FAILED;
            errorMessage = limitText(e.getMessage());
            log.error("Scheduled agent task failed: taskId={}", taskId, e);
        } finally {
            finish(task, run, lockOwner, force, status, reportId, outputSummary, errorMessage);
        }
    }

    private ScheduledAgentTaskRun startRun(
            Integer taskId, String sessionId, LocalDateTime startedAt) {
        ScheduledAgentTaskRun run = new ScheduledAgentTaskRun();
        run.setTaskId(taskId);
        run.setSessionId(sessionId);
        run.setStatus(RUNNING);
        run.setStartedAt(startedAt);
        runMapper.insert(run);
        return run;
    }

    private void finish(
            ScheduledAgentTask task,
            ScheduledAgentTaskRun run,
            String lockOwner,
            boolean force,
            String status,
            Integer reportId,
            String outputSummary,
            String errorMessage) {
        LocalDateTime finishedAt = LocalDateTime.now();
        runMapper.finish(run.getId(), status, reportId, outputSummary, errorMessage, finishedAt);
        LocalDateTime nextRunAt =
                force && task.getNextRunAt().isAfter(finishedAt)
                        ? task.getNextRunAt()
                        : scheduleCalculator.nextRunAfter(
                                task.getScheduleType(),
                                task.getScheduleExpr(),
                                finishedAt,
                                task.getTimezone());
        int updated =
                taskMapper.finishRun(
                        task.getId(),
                        lockOwner,
                        run.getId(),
                        nextRunAt,
                        finishedAt,
                        status,
                        errorMessage);
        if (updated == 0) {
            log.warn(
                    "Scheduled agent task lock changed before finish: taskId={}, runId={}",
                    task.getId(),
                    run.getId());
        }
    }

    private String resolveSessionId(ScheduledAgentTask task) {
        if (ScheduledAgentTaskServiceImpl.FIXED_SESSION.equals(task.getSessionMode())) {
            return task.getSessionId();
        }
        return "scheduled-task-" + task.getId() + "-" + UUID.randomUUID();
    }

    private String buildPrompt(ScheduledAgentTask task) {
        return task.getPrompt() + "\n\n定时任务执行要求：如果信息不足或需要用户确认，请直接说明无法完成，不要调用 ask_user 反问用户。";
    }

    private String resolveStatus(List<ChatStreamEvent> events) {
        if (events == null) {
            return FAILED;
        }
        if (events.stream().anyMatch(event -> event.type() == ChatStreamEventType.ERROR)) {
            return FAILED;
        }
        if (events.stream().anyMatch(event -> event.type() == ChatStreamEventType.QUESTION)) {
            return NEEDS_USER;
        }
        return SUCCESS;
    }

    private Integer extractReportId(List<ChatStreamEvent> events) {
        if (events == null) {
            return null;
        }
        return events.stream()
                .filter(event -> event.type() == ChatStreamEventType.REPORT)
                .map(ChatStreamEvent::content)
                .filter(
                        content ->
                                content != null
                                        && content.startsWith(ToolCallConstants.SUCCESS_PREFIX))
                .map(content -> content.substring(ToolCallConstants.SUCCESS_PREFIX.length()).trim())
                .map(Integer::valueOf)
                .findFirst()
                .orElse(null);
    }

    private String extractOutput(List<ChatStreamEvent> events) {
        if (events == null) {
            return null;
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            ChatStreamEvent event = events.get(i);
            if ((event.type() == ChatStreamEventType.SUMMARY
                            || event.type() == ChatStreamEventType.TEXT)
                    && event.content() != null
                    && !event.content().isBlank()) {
                return event.content();
            }
        }
        return null;
    }

    private String extractError(List<ChatStreamEvent> events) {
        if (events == null) {
            return "Agent stream returned no events.";
        }
        return events.stream()
                .filter(event -> event.type() == ChatStreamEventType.ERROR)
                .map(ChatStreamEvent::content)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String limitText(String text) {
        if (text == null || text.length() <= SUMMARY_LIMIT) {
            return text;
        }
        return text.substring(0, SUMMARY_LIMIT);
    }
}

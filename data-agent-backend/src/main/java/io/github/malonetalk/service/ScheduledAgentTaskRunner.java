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
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.entity.ScheduledAgentTask;
import io.github.malonetalk.enums.ChatStreamEventType;
import io.github.malonetalk.enums.ScheduledAgentTaskStatus;
import io.github.malonetalk.mapper.ScheduledAgentTaskMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledAgentTaskRunner {

    private final AgentService agentService;
    private final ScheduledAgentTaskMapper taskMapper;
    private final ScheduledAgentScheduleCalculator scheduleCalculator;

    @Value("${data-agent.schedule.lock-duration:PT30M}")
    private Duration lockDuration = Duration.ofMinutes(30);

    public ClaimedRun claim(Integer taskId, boolean force) {
        LocalDateTime startedAt = LocalDateTime.now();
        String lockOwner = UUID.randomUUID().toString();
        if (taskMapper.lockForRun(
                        taskId,
                        startedAt,
                        startedAt.plus(effectiveLockDuration()),
                        lockOwner,
                        force)
                == 0) {
            return null;
        }

        ScheduledAgentTask task = taskMapper.selectById(taskId);
        String sessionId = "scheduled-task-" + task.getId() + "-" + UUID.randomUUID();
        return new ClaimedRun(task, sessionId, lockOwner, force);
    }

    public void run(ClaimedRun claimedRun) {
        ScheduledAgentTaskStatus status = ScheduledAgentTaskStatus.SUCCESS;
        try {
            List<ChatStreamEvent> events =
                    agentService
                            .chatStream(
                                    claimedRun.sessionId(),
                                    buildPrompt(claimedRun.task()),
                                    null,
                                    false)
                            .collectList()
                            .block();
            status = resolveStatus(events);
        } catch (Exception e) {
            status = ScheduledAgentTaskStatus.FAILED;
            log.error("Scheduled agent task failed: taskId={}", claimedRun.task().getId(), e);
        } finally {
            finish(claimedRun.task(), claimedRun.lockOwner(), claimedRun.force(), status);
        }
    }

    public void reject(ClaimedRun claimedRun) {
        finish(
                claimedRun.task(),
                claimedRun.lockOwner(),
                claimedRun.force(),
                ScheduledAgentTaskStatus.FAILED);
    }

    private void finish(
            ScheduledAgentTask task,
            String lockOwner,
            boolean force,
            ScheduledAgentTaskStatus status) {
        LocalDateTime finishedAt = LocalDateTime.now();
        LocalDateTime nextRunAt =
                force && task.getNextRunAt().isAfter(finishedAt)
                        ? task.getNextRunAt()
                        : scheduleCalculator.nextRunAfter(
                                task.getScheduleType(),
                                task.getScheduleExpr(),
                                finishedAt,
                                task.getTimezone());
        int updated =
                taskMapper.finishRun(task.getId(), lockOwner, nextRunAt, finishedAt, status.name());
        if (updated == 0) {
            log.warn("Scheduled agent task lock changed before finish: taskId={}", task.getId());
        }
    }

    private String buildPrompt(ScheduledAgentTask task) {
        return task.getPrompt() + "\n\n定时任务执行要求：如果信息不足或需要用户确认，请直接说明无法完成，不要调用 ask_user 反问用户。";
    }

    private ScheduledAgentTaskStatus resolveStatus(List<ChatStreamEvent> events) {
        if (events == null) {
            return ScheduledAgentTaskStatus.FAILED;
        }
        if (events.stream().anyMatch(event -> event.type() == ChatStreamEventType.ERROR)) {
            return ScheduledAgentTaskStatus.FAILED;
        }
        if (events.stream().anyMatch(event -> event.type() == ChatStreamEventType.QUESTION)) {
            return ScheduledAgentTaskStatus.NEEDS_USER;
        }
        return ScheduledAgentTaskStatus.SUCCESS;
    }

    private Duration effectiveLockDuration() {
        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalStateException("data-agent.schedule.lock-duration must be positive.");
        }
        return lockDuration;
    }

    public record ClaimedRun(
            ScheduledAgentTask task, String sessionId, String lockOwner, boolean force) {}
}

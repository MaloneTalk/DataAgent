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
import io.github.malonetalk.entity.ScheduledAgentTask;
import io.github.malonetalk.mapper.ScheduledAgentTaskMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class DatabasePollingScheduledAgentTaskScheduler implements ScheduledAgentTaskScheduler {

    private static final int BATCH_SIZE = 20;
    private static final int POOL_SIZE = 3;
    private static final int QUEUE_CAPACITY = 20;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final ScheduledAgentTaskMapper taskMapper;
    private final AgentService agentService;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    @PostConstruct
    public void initExecutor() {
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("scheduled-agent-task-");
        executor.initialize();
    }

    @Scheduled(fixedDelayString = "${data-agent.schedule.dispatch-delay-ms:10000}")
    public void dispatchDueTasks() {
        for (Integer taskId : taskMapper.findDueTaskIds(LocalDateTime.now(), BATCH_SIZE)) {
            ClaimedRun claimedRun = claim(taskId, false);
            if (claimedRun != null && !execute(claimedRun)) {
                return;
            }
        }
    }

    @Override
    public boolean runNow(Integer taskId) {
        ClaimedRun claimedRun = claim(taskId, true);
        if (claimedRun == null) {
            return false;
        }
        return execute(claimedRun);
    }

    private boolean execute(ClaimedRun claimedRun) {
        try {
            executor.execute(() -> run(claimedRun));
            return true;
        } catch (TaskRejectedException e) {
            finish(claimedRun.task(), claimedRun.lockOwner(), claimedRun.force());
            log.warn("Scheduled agent task executor rejected taskId={}", claimedRun.task().getId());
            return false;
        }
    }

    private ClaimedRun claim(Integer taskId, boolean force) {
        LocalDateTime startedAt = LocalDateTime.now();
        String lockOwner = UUID.randomUUID().toString();
        if (taskMapper.lockForRun(
                        taskId, startedAt, startedAt.plus(LOCK_DURATION), lockOwner, force)
                == 0) {
            return null;
        }

        ScheduledAgentTask task = taskMapper.selectById(taskId);
        return new ClaimedRun(task, lockOwner, force);
    }

    private void run(ClaimedRun claimedRun) {
        try {
            agentService
                    .chatStream(
                            "scheduled-task-" + claimedRun.task().getId() + "-" + UUID.randomUUID(),
                            claimedRun.task().getPrompt()
                                    + "\n\n"
                                    + "Scheduled task requirement: if information is insufficient"
                                    + " or user confirmation is needed, state that the task cannot"
                                    + " be completed; do not call ask_user.",
                            null,
                            false)
                    .then()
                    .block();
        } catch (Exception e) {
            log.error("Scheduled agent task failed: taskId={}", claimedRun.task().getId(), e);
        } finally {
            finish(claimedRun.task(), claimedRun.lockOwner(), claimedRun.force());
        }
    }

    private void finish(ScheduledAgentTask task, String lockOwner, boolean force) {
        LocalDateTime finishedAt = LocalDateTime.now();
        LocalDateTime nextRunAt =
                force && task.getNextRunAt().isAfter(finishedAt)
                        ? task.getNextRunAt()
                        : ScheduledAgentScheduleCalculator.nextRunAfter(
                                task.getScheduleType(), task.getScheduleExpr(), finishedAt);
        int updated = taskMapper.finishRun(task.getId(), lockOwner, nextRunAt, finishedAt);
        if (updated == 0) {
            log.warn("Scheduled agent task lock changed before finish: taskId={}", task.getId());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    private record ClaimedRun(ScheduledAgentTask task, String lockOwner, boolean force) {}
}

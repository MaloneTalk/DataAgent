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

import io.github.malonetalk.entity.ScheduledAgentTask;
import io.github.malonetalk.mapper.ScheduledAgentTaskMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class DatabaseScheduledAgentTaskRunner {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String REJECTED_MESSAGE = "Task executor rejected the run.";

    private final ScheduledAgentTaskMapper taskMapper;
    private final ScheduledAgentTaskExecutor taskExecutor;

    @Value("${data-agent.schedule.lock-duration}")
    private Duration lockDuration;

    boolean runDue(Integer taskId) {
        ClaimedRun claimedRun = claim(taskId);
        return claimedRun == null || execute(claimedRun);
    }

    boolean runNow(Integer taskId) {
        ClaimedRun claimedRun = claimManual(taskId);
        return claimedRun != null && execute(claimedRun);
    }

    private boolean execute(ClaimedRun claimedRun) {
        CompletableFuture<ScheduledAgentTaskExecutor.Result> result;
        try {
            result = taskExecutor.execute(claimedRun.task());
        } catch (RejectedExecutionException e) {
            finish(claimedRun, STATUS_FAILED, REJECTED_MESSAGE);
            log.warn("Scheduled agent task executor rejected taskId={}", claimedRun.task().getId());
            return false;
        }

        result.whenComplete((runResult, throwable) -> finish(claimedRun, runResult, throwable));
        return true;
    }

    private ClaimedRun claim(Integer taskId) {
        LocalDateTime startedAt = LocalDateTime.now();
        String lockOwner = UUID.randomUUID().toString();
        if (taskMapper.lockForRun(taskId, startedAt, startedAt.plus(lockDuration), lockOwner)
                == 0) {
            return null;
        }

        ScheduledAgentTask task = taskMapper.selectById(taskId);
        return new ClaimedRun(task, lockOwner, false);
    }

    private ClaimedRun claimManual(Integer taskId) {
        LocalDateTime startedAt = LocalDateTime.now();
        String lockOwner = UUID.randomUUID().toString();
        if (taskMapper.lockForManualRun(taskId, startedAt, startedAt.plus(lockDuration), lockOwner)
                == 0) {
            return null;
        }

        ScheduledAgentTask task = taskMapper.selectById(taskId);
        return new ClaimedRun(task, lockOwner, true);
    }

    private void finish(
            ClaimedRun claimedRun, ScheduledAgentTaskExecutor.Result result, Throwable throwable) {
        if (throwable != null) {
            finish(claimedRun, STATUS_FAILED, rootCauseMessage(throwable));
            return;
        }

        finish(claimedRun, result.success() ? STATUS_SUCCESS : STATUS_FAILED, result.error());
    }

    private void finish(ClaimedRun claimedRun, String lastStatus, String lastError) {
        ScheduledAgentTask task = claimedRun.task();
        LocalDateTime finishedAt = LocalDateTime.now();
        LocalDateTime currentNextRunAt = task.getNextRunAt();
        LocalDateTime nextRunAt =
                claimedRun.force()
                                && currentNextRunAt != null
                                && currentNextRunAt.isAfter(finishedAt)
                        ? currentNextRunAt
                        : ScheduledAgentScheduleCalculator.nextRunAfter(
                                task.getScheduleType(), task.getScheduleExpr(), finishedAt);
        int updated =
                taskMapper.finishRun(
                        task.getId(),
                        claimedRun.lockOwner(),
                        nextRunAt,
                        finishedAt,
                        lastStatus,
                        lastError);
        if (updated == 0) {
            log.warn("Scheduled agent task lock changed before finish: taskId={}", task.getId());
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        return rootCause.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private record ClaimedRun(ScheduledAgentTask task, String lockOwner, boolean force) {}
}

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

import io.github.malonetalk.mapper.ScheduledAgentTaskMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabasePollingScheduledAgentTaskScheduler {

    private static final int BATCH_SIZE = 20;

    private final ScheduledAgentTaskMapper taskMapper;
    private final ScheduledAgentTaskRunner taskRunner;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    @Value("${data-agent.schedule.executor.core-size:3}")
    private int corePoolSize;

    @Value("${data-agent.schedule.executor.max-size:3}")
    private int maxPoolSize;

    @Value("${data-agent.schedule.executor.queue-capacity:20}")
    private int queueCapacity;

    @PostConstruct
    public void initExecutor() {
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("scheduled-agent-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
    }

    @Scheduled(fixedDelayString = "${data-agent.schedule.dispatch-delay-ms:10000}")
    public void dispatchDueTasks() {
        for (var task : taskMapper.findDueTasks(LocalDateTime.now(), BATCH_SIZE)) {
            if (!hasCapacity()) {
                return;
            }
            ScheduledAgentTaskRunner.ClaimedRun claimedRun = taskRunner.claim(task.getId(), false);
            if (claimedRun != null && !execute(claimedRun)) {
                return;
            }
        }
    }

    public boolean runNow(Integer taskId) {
        if (!hasCapacity()) {
            return false;
        }
        ScheduledAgentTaskRunner.ClaimedRun claimedRun = taskRunner.claim(taskId, true);
        if (claimedRun == null) {
            return false;
        }
        return execute(claimedRun);
    }

    private boolean execute(ScheduledAgentTaskRunner.ClaimedRun claimedRun) {
        try {
            executor.execute(() -> taskRunner.run(claimedRun));
            return true;
        } catch (TaskRejectedException e) {
            taskRunner.reject(claimedRun, "Scheduled task executor rejected the run.");
            log.warn("Scheduled agent task executor rejected taskId={}", claimedRun.task().getId());
            return false;
        }
    }

    private boolean hasCapacity() {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        return pool.getActiveCount() < pool.getMaximumPoolSize()
                || pool.getQueue().remainingCapacity() > 0;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}

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
import io.github.malonetalk.agent.TaskType;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.entity.ScheduledAgentTask;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledAgentTaskExecutor {

    private final AgentService agentService;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    @Value("${data-agent.schedule.executor.core-pool-size}")
    private int corePoolSize;

    @Value("${data-agent.schedule.executor.max-pool-size}")
    private int maxPoolSize;

    @Value("${data-agent.schedule.executor.queue-capacity}")
    private int queueCapacity;

    @PostConstruct
    void initExecutor() {
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("scheduled-agent-task-");
        executor.initialize();
    }

    public CompletableFuture<Result> execute(ScheduledAgentTask task) {
        return CompletableFuture.supplyAsync(() -> run(task), executor);
    }

    private Result run(ScheduledAgentTask task) {
        try {
            runAgent(task);
            return Result.succeeded();
        } catch (Exception e) {
            log.error("Scheduled agent task failed: taskId={}", task.getId(), e);
            return Result.failed(rootCauseMessage(e));
        }
    }

    private void runAgent(ScheduledAgentTask task) {
        agentService
                .chatStream(
                        ToolCallContext.builder()
                                .sessionId(
                                        "scheduled-task-" + task.getId() + "-" + UUID.randomUUID())
                                .userInput(task.getPrompt())
                                .taskType(TaskType.SCHEDULED)
                                .build(),
                        null)
                .blockLast();
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

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    @Builder
    public record Result(boolean success, String error) {

        private static Result succeeded() {
            return Result.builder().success(true).build();
        }

        private static Result failed(String error) {
            return Result.builder().success(false).error(error).build();
        }
    }
}

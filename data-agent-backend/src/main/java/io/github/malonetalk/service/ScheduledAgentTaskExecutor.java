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
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.config.ScheduledAgentScheduleProperties;
import io.github.malonetalk.entity.ScheduledAgentTask;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledAgentTaskExecutor {

    private final AgentService agentService;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    private final ScheduledAgentScheduleProperties scheduleProperties;

    @PostConstruct
    void initExecutor() {
        executor.setCorePoolSize(scheduleProperties.getExecutor().getCorePoolSize());
        executor.setMaxPoolSize(scheduleProperties.getExecutor().getMaxPoolSize());
        executor.setQueueCapacity(scheduleProperties.getExecutor().getQueueCapacity());
        executor.setThreadNamePrefix("scheduled-agent-task-");
        executor.initialize();
    }

    public CompletableFuture<Void> execute(ScheduledAgentTask task) {
        return CompletableFuture.runAsync(() -> runAgent(task), executor);
    }

    private void runAgent(ScheduledAgentTask task) {
        agentService
                .chatStream(
                        ToolCallContext.builder()
                                .sessionId(
                                        "scheduled-task-" + task.getId() + "-" + UUID.randomUUID())
                                .userInput(task.getPrompt())
                                .scheduled(true)
                                .build(),
                        null)
                .blockLast();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}

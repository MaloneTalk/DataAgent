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
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledAgentTaskDispatcher {

    private static final int BATCH_SIZE = 20;

    private final ScheduledAgentTaskMapper taskMapper;
    private final ScheduledAgentTaskRunner taskRunner;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @Scheduled(fixedDelayString = "${data-agent.schedule.dispatch-delay-ms:10000}")
    public void dispatchDueTasks() {
        for (ScheduledAgentTask task : taskMapper.findDueTasks(LocalDateTime.now(), BATCH_SIZE)) {
            executor.execute(() -> taskRunner.run(task.getId(), false));
        }
    }

    public void runNow(Integer taskId) {
        executor.execute(() -> taskRunner.run(taskId, true));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}

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
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "data-agent.schedule.dispatcher",
        havingValue = "database",
        matchIfMissing = true)
@RequiredArgsConstructor
class DatabaseScheduledAgentTaskDispatcher {

    private final ScheduledAgentTaskMapper taskMapper;
    private final DatabaseScheduledAgentTaskRunner taskRunner;

    @Value("${data-agent.schedule.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${data-agent.schedule.dispatch-delay-ms}")
    public void dispatchDueTasks() {
        for (Integer taskId : taskMapper.findDueTaskIds(LocalDateTime.now(), batchSize)) {
            if (!taskRunner.runDue(taskId)) {
                return;
            }
        }
    }
}

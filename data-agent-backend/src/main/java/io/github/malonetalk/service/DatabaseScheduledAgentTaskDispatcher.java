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

import static io.github.malonetalk.common.Constants.SCHEDULE_PROPERTIES_PREFIX;

import io.github.malonetalk.config.ScheduledAgentScheduleProperties;
import io.github.malonetalk.mapper.ScheduledAgentTaskMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = SCHEDULE_PROPERTIES_PREFIX,
        name = "dispatcher",
        havingValue = "database",
        matchIfMissing = true)
@RequiredArgsConstructor
class DatabaseScheduledAgentTaskDispatcher {

    private final ScheduledAgentTaskMapper taskMapper;
    private final DatabaseScheduledAgentTaskRunner taskRunner;
    private final ScheduledAgentScheduleProperties scheduleProperties;

    @Scheduled(fixedDelayString = "#{@scheduledAgentScheduleProperties.dispatchDelayMs}")
    public void dispatchDueTasks() {
        for (Integer taskId :
                taskMapper.findDueTaskIds(LocalDateTime.now(), scheduleProperties.getBatchSize())) {
            taskRunner.runDue(taskId);
        }
    }
}

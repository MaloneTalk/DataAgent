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
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ScheduledAgentTaskScheduler {

    private final ScheduledAgentTaskSchedulerStrategy selectedStrategy;

    public ScheduledAgentTaskScheduler(
            List<ScheduledAgentTaskSchedulerStrategy> strategies,
            @Value("${data-agent.schedule.scheduler-type:db-polling}") String schedulerType) {
        this.selectedStrategy = select(strategies, schedulerType);
    }

    public void sync(ScheduledAgentTask task) {
        selectedStrategy.sync(task);
    }

    public void unschedule(Integer taskId) {
        selectedStrategy.unschedule(taskId);
    }

    public void runNow(Integer taskId) {
        selectedStrategy.runNow(taskId);
    }

    private ScheduledAgentTaskSchedulerStrategy select(
            List<ScheduledAgentTaskSchedulerStrategy> strategies, String schedulerType) {
        String selectedType = normalize(schedulerType);
        return strategies.stream()
                .filter(strategy -> selectedType.equals(normalize(strategy.type())))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Unsupported scheduled task scheduler type: "
                                                + schedulerType));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

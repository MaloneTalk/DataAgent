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
package io.github.malonetalk.dto;

import io.github.malonetalk.entity.ScheduledAgentTask;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record ScheduledAgentTaskResponse(
        Integer id,
        String name,
        String prompt,
        String scheduleType,
        String scheduleExpr,
        Boolean enabled,
        Boolean running,
        LocalDateTime nextRunAt,
        LocalDateTime lastRunAt,
        String lastStatus,
        String lastError) {

    public static ScheduledAgentTaskResponse from(ScheduledAgentTask task) {
        return ScheduledAgentTaskResponse.builder()
                .id(task.getId())
                .name(task.getName())
                .prompt(task.getPrompt())
                .scheduleType(task.getScheduleType())
                .scheduleExpr(task.getScheduleExpr())
                .enabled(task.getEnabled())
                .running(task.getRunning())
                .nextRunAt(task.getNextRunAt())
                .lastRunAt(task.getLastRunAt())
                .lastStatus(task.getLastStatus())
                .lastError(task.getLastError())
                .build();
    }
}

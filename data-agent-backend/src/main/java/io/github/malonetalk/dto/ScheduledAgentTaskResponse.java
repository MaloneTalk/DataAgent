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

import io.github.malonetalk.enums.ScheduledAgentScheduleType;
import io.github.malonetalk.enums.ScheduledAgentSessionMode;
import io.github.malonetalk.enums.ScheduledAgentTaskStatus;
import java.time.LocalDateTime;

public record ScheduledAgentTaskResponse(
        Integer id,
        String name,
        String prompt,
        ScheduledAgentScheduleType scheduleType,
        String scheduleExpr,
        String timezone,
        Boolean enabled,
        Boolean running,
        ScheduledAgentSessionMode sessionMode,
        String sessionId,
        LocalDateTime nextRunAt,
        LocalDateTime lastRunAt,
        ScheduledAgentTaskStatus lastStatus,
        String lastError,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}

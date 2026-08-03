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

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class ScheduledAgentScheduleCalculator {

    public static final String DAILY = "DAILY";
    public static final String INTERVAL = "INTERVAL";
    public static final String CRON = "CRON";

    public LocalDateTime nextRunAfter(String type, String expr, LocalDateTime after) {
        return switch (normalizeType(type)) {
            case DAILY -> nextDaily(expr, after);
            case INTERVAL -> after.plus(Duration.parse(expr.trim()));
            case CRON -> nextCron(expr, after);
            default -> throw invalidSchedule("Unsupported schedule type: " + type);
        };
    }

    public String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw invalidSchedule("scheduleType cannot be blank.");
        }
        return type.trim().toUpperCase();
    }

    private LocalDateTime nextDaily(String expr, LocalDateTime after) {
        LocalTime time = LocalTime.parse(expr.trim());
        LocalDateTime next = after.toLocalDate().atTime(time);
        return next.isAfter(after) ? next : next.plusDays(1);
    }

    private LocalDateTime nextCron(String expr, LocalDateTime after) {
        LocalDateTime next = CronExpression.parse(expr.trim()).next(after);
        if (next == null) {
            throw invalidSchedule("Cron expression has no next run time.");
        }
        return next;
    }

    private BusinessException invalidSchedule(String message) {
        return BusinessException.of(ErrorCode.BAD_REQUEST, message);
    }
}

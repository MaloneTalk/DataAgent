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
import io.github.malonetalk.enums.ScheduledAgentScheduleType;
import io.github.malonetalk.exception.BusinessException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.scheduling.support.CronExpression;

public final class ScheduledAgentScheduleCalculator {

    private ScheduledAgentScheduleCalculator() {}

    public static LocalDateTime nextRunAfter(String type, String expr, LocalDateTime after) {
        return switch (ScheduledAgentScheduleType.valueOf(type)) {
            case DAILY -> nextDaily(expr, after);
            case INTERVAL -> after.plus(parsePositiveDuration(expr));
            case CRON -> nextCron(expr, after);
        };
    }

    private static LocalDateTime nextDaily(String expr, LocalDateTime after) {
        LocalTime time = parseDailyTime(expr);
        LocalDateTime next = after.toLocalDate().atTime(time);
        return next.isAfter(after) ? next : next.plusDays(1);
    }

    private static LocalDateTime nextCron(String expr, LocalDateTime after) {
        LocalDateTime next;
        try {
            next = CronExpression.parse(requireScheduleExpr(expr)).next(after);
        } catch (IllegalArgumentException e) {
            throw invalidSchedule("Invalid cron expression: " + expr);
        }
        if (next == null) {
            throw invalidSchedule("Cron expression has no next run time.");
        }
        return next;
    }

    private static LocalTime parseDailyTime(String expr) {
        try {
            return LocalTime.parse(requireScheduleExpr(expr));
        } catch (DateTimeException e) {
            throw invalidSchedule("Invalid daily schedule time: " + expr);
        }
    }

    private static Duration parsePositiveDuration(String expr) {
        Duration duration;
        try {
            duration = Duration.parse(requireScheduleExpr(expr));
        } catch (DateTimeException e) {
            throw invalidSchedule("Invalid interval duration: " + expr);
        }
        if (duration.isZero() || duration.isNegative()) {
            throw invalidSchedule("Interval duration must be positive.");
        }
        return duration;
    }

    private static String requireScheduleExpr(String expr) {
        if (expr == null || expr.isBlank()) {
            throw invalidSchedule("scheduleExpr cannot be blank.");
        }
        return expr.trim();
    }

    private static BusinessException invalidSchedule(String message) {
        return BusinessException.of(ErrorCode.BAD_REQUEST, message);
    }
}

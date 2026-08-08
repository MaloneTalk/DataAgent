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
package io.github.malonetalk.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DateInfoTool implements MarkAgentTool {

    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final String PUBLIC_HOLIDAY = "PUBLIC_HOLIDAY";
    private static final String ADJUSTED_WORKDAY = "ADJUSTED_WORKDAY";
    private static final TypeReference<HolidayCalendar> HOLIDAY_CALENDAR_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<Integer, Optional<HolidayCalendar>> calendars = new ConcurrentHashMap<>();

    @Autowired
    public DateInfoTool(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemDefaultZone());
    }

    DateInfoTool(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Tool(
            name = "get_date_info",
            description =
                    """
                    Get accurate date information, including weekday, weekend, Chinese public \
                    holidays, adjusted workdays, festivals, solar terms and important days. Use \
                    this whenever the user asks about today, a date, weekday, whether a day is a \
                    holiday, or Chinese holiday schedule. For relative dates, convert them using \
                    the system prompt's current date before passing date; omit date for today.\
                    """)
    public String getDateInfo(
            @ToolParam(
                            name = "date",
                            description = "Date in yyyy-MM-dd format. Defaults to today.",
                            required = false)
                    String date,
            @ToolParam(
                            name = "timezone",
                            description =
                                    "IANA timezone, e.g. Asia/Shanghai. Defaults to Asia/Shanghai.",
                            required = false)
                    String timezone) {
        try {
            return objectMapper.writeValueAsString(resolve(date, timezone));
        } catch (Exception e) {
            return "Error: failed to get date info: " + e.getMessage();
        }
    }

    DateInfo resolve(String dateText, String timezoneText) {
        ZoneId zoneId =
                ZoneId.of(StringUtils.hasText(timezoneText) ? timezoneText : DEFAULT_TIMEZONE);
        LocalDate date =
                StringUtils.hasText(dateText)
                        ? LocalDate.parse(dateText)
                        : LocalDate.now(clock.withZone(zoneId));
        Optional<HolidayCalendar> calendar = calendar(date.getYear());
        HolidayInfo holidayInfo = calendar.map(c -> c.days().get(date.toString())).orElse(null);
        List<ImportantDay> importantDays =
                calendar.map(c -> events(c).getOrDefault(date.toString(), List.of()))
                        .orElse(List.of());

        boolean legalHoliday = holidayInfo != null && PUBLIC_HOLIDAY.equals(holidayInfo.type());
        boolean adjustedWorkday =
                holidayInfo != null && ADJUSTED_WORKDAY.equals(holidayInfo.type());
        boolean weekend = date.getDayOfWeek().getValue() >= 6;
        boolean dayOff = legalHoliday || (weekend && !adjustedWorkday);

        return new DateInfo(
                date.toString(),
                zoneId.getId(),
                date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA),
                date.getDayOfWeek().getValue(),
                weekend,
                dayOff,
                legalHoliday,
                adjustedWorkday,
                holidayInfo != null ? holidayInfo.name() : null,
                dayType(legalHoliday, adjustedWorkday, weekend),
                importantDays,
                calendar.isPresent());
    }

    private Optional<HolidayCalendar> calendar(int year) {
        return calendars.computeIfAbsent(year, this::loadCalendar);
    }

    private Optional<HolidayCalendar> loadCalendar(int year) {
        String path = "holidays/cn/" + year + ".json";
        try (InputStream input =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(input, HOLIDAY_CALENDAR_TYPE));
        } catch (IOException e) {
            throw new IllegalStateException("failed to load holiday data: " + path, e);
        }
    }

    private String dayType(boolean legalHoliday, boolean adjustedWorkday, boolean weekend) {
        if (legalHoliday) {
            return "PUBLIC_HOLIDAY";
        }
        if (adjustedWorkday) {
            return "ADJUSTED_WORKDAY";
        }
        return weekend ? "WEEKEND" : "WORKDAY";
    }

    private Map<String, List<ImportantDay>> events(HolidayCalendar calendar) {
        return calendar.events() != null ? calendar.events() : Map.of();
    }

    record DateInfo(
            String date,
            String timezone,
            String weekday,
            int weekdayIso,
            boolean isWeekend,
            boolean isDayOff,
            boolean isLegalHoliday,
            boolean isAdjustedWorkday,
            String holidayName,
            String dayType,
            List<ImportantDay> importantDays,
            boolean holidayDataAvailable) {}

    record HolidayCalendar(
            int year,
            String source,
            String sourceUrl,
            Map<String, HolidayInfo> days,
            Map<String, List<ImportantDay>> events) {}

    record HolidayInfo(String type, String name) {}

    record ImportantDay(String type, String name, String time, String note) {}
}

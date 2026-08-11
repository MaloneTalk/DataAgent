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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DateInfoTool implements MarkAgentTool {

    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final String HOLIDAY_API_BASE_URL = "https://timor.tech/api/holiday/info";

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient httpClient;

    @Autowired
    public DateInfoTool(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemDefaultZone(), HttpClient.newHttpClient());
    }

    DateInfoTool(ObjectMapper objectMapper, Clock clock, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.httpClient = httpClient;
    }

    @Tool(
            name = "get_date_info",
            description =
                    """
                    Get accurate date information, including weekday, weekend, Chinese public \
                    holidays, holiday schedule adjustments, and optional difference to another \
                    date. Use this whenever the user asks about today, a date, weekday, whether a \
                    day is a holiday, Chinese holiday schedule, or date difference. For relative \
                    dates, convert them using the system prompt's current date before passing \
                    date; omit date for today. Pass end_date only when a calendar-day difference \
                    is needed. Date difference counts start date inclusive and end date exclusive.\
                    """)
    public String getDateInfo(
            @ToolParam(
                            name = "date",
                            description =
                                    "Date in yyyy-MM-dd format. Defaults to today. Also acts as"
                                            + " start date when end_date is provided.",
                            required = false)
                    String date,
            @ToolParam(
                            name = "end_date",
                            description =
                                    "Optional end date in yyyy-MM-dd format for date difference"
                                            + " calculation.",
                            required = false)
                    String endDate,
            @ToolParam(
                            name = "timezone",
                            description =
                                    "IANA timezone, e.g. Asia/Shanghai. Defaults to Asia/Shanghai.",
                            required = false)
                    String timezone) {
        try {
            return objectMapper.writeValueAsString(resolve(date, endDate, timezone));
        } catch (Exception e) {
            return "Error: failed to get date info: " + e.getMessage();
        }
    }

    DateInfo resolve(String dateText, String timezoneText) {
        return resolve(dateText, null, timezoneText);
    }

    DateInfo resolve(String dateText, String endDateText, String timezoneText) {
        ZoneId zoneId =
                ZoneId.of(StringUtils.hasText(timezoneText) ? timezoneText : DEFAULT_TIMEZONE);
        LocalDate date =
                StringUtils.hasText(dateText)
                        ? LocalDate.parse(dateText)
                        : LocalDate.now(clock.withZone(zoneId));
        Optional<HolidayApiResponse> holidayApiResponse = queryHolidayApi(date);
        HolidayDetail holiday = holidayApiResponse.map(HolidayApiResponse::holiday).orElse(null);

        boolean legalHoliday = holiday != null && Boolean.TRUE.equals(holiday.holiday());
        boolean adjustedWorkday = holiday != null && Boolean.FALSE.equals(holiday.holiday());
        boolean weekend = date.getDayOfWeek().getValue() >= 6;

        return new DateInfo(
                date.toString(),
                date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA),
                holiday != null ? holiday.name() : null,
                dayType(legalHoliday, adjustedWorkday, weekend),
                holidayApiResponse.isPresent(),
                StringUtils.hasText(endDateText)
                        ? resolveDateDiff(date, LocalDate.parse(endDateText))
                        : null);
    }

    DateDiff resolveDateDiff(LocalDate startDate, LocalDate endDate) {
        long calendarDays = ChronoUnit.DAYS.between(startDate, endDate);

        return new DateDiff(calendarDays, Math.abs(calendarDays));
    }

    private Optional<HolidayApiResponse> queryHolidayApi(LocalDate date) {
        HttpRequest request =
                HttpRequest.newBuilder(holidayInfoUri(date))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            HolidayApiResponse body =
                    objectMapper.readValue(response.body(), HolidayApiResponse.class);
            return body.code() == 0 ? Optional.of(body) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private URI holidayInfoUri(LocalDate date) {
        return URI.create(HOLIDAY_API_BASE_URL + "/" + date);
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

    record DateInfo(
            String date,
            String weekday,
            String holidayName,
            String dayType,
            boolean holidayDataAvailable,
            DateDiff dateDiff) {}

    record DateDiff(long calendarDays, long absoluteCalendarDays) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HolidayApiResponse(int code, HolidayType type, HolidayDetail holiday) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HolidayType(Integer type, String name, Integer week) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HolidayDetail(Boolean holiday, String name, String target, String date) {}
}

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
import lombok.Builder;
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
                    start_date; omit start_date for today. Pass end_date only when a calendar-day \
                    difference is needed. Date difference counts start_date inclusive and \
                    end_date exclusive.\
                    """)
    public String getDateInfo(
            @ToolParam(
                            name = "start_date",
                            description =
                                    "Date in yyyy-MM-dd format. Defaults to today. When end_date"
                                            + " is provided, this acts as the start of the range.",
                            required = false)
                    String startDate,
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
            return objectMapper.writeValueAsString(resolve(startDate, endDate, timezone));
        } catch (Exception e) {
            return "Error: failed to get date info: " + e.getMessage();
        }
    }

    DateInfo resolve(String startDateText, String endDateText, String timezoneText) {
        ZoneId zoneId =
                ZoneId.of(StringUtils.hasText(timezoneText) ? timezoneText : DEFAULT_TIMEZONE);
        LocalDate startDate =
                StringUtils.hasText(startDateText)
                        ? LocalDate.parse(startDateText)
                        : LocalDate.now(clock.withZone(zoneId));
        Optional<HolidayApiResponse> holidayApiResponse = queryHolidayApi(startDate);
        HolidayDetail holiday = holidayApiResponse.map(HolidayApiResponse::holiday).orElse(null);

        boolean legalHoliday = holiday != null && Boolean.TRUE.equals(holiday.holiday());
        // API returns holiday={holiday:false} only for adjusted workdays (调休补班);
        // regular non-holiday days return holiday=null.
        boolean adjustedWorkday = holiday != null && Boolean.FALSE.equals(holiday.holiday());
        boolean weekend = startDate.getDayOfWeek().getValue() >= 6;

        return DateInfo.builder()
                .date(startDate.toString())
                .weekday(startDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA))
                .holidayName(holiday != null ? holiday.name() : null)
                .dayType(dayType(legalHoliday, adjustedWorkday, weekend))
                .holidayDataAvailable(holidayApiResponse.isPresent())
                .dateDiff(
                        StringUtils.hasText(endDateText)
                                ? resolveDateDiff(startDate, LocalDate.parse(endDateText))
                                : null)
                .build();
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

    @Builder
    record DateInfo(
            String date,
            String weekday,
            String holidayName,
            String dayType,
            boolean holidayDataAvailable,
            DateDiff dateDiff) {}

    record DateDiff(long calendarDays, long absoluteCalendarDays) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HolidayApiResponse(int code, HolidayDetail holiday) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HolidayDetail(Boolean holiday, String name) {}
}

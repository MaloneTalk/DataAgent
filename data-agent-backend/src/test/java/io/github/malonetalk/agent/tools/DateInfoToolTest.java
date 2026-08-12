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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DateInfoToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    // --- Date diff ---

    @Test
    void dateDiff_positive() {
        DateInfoTool tool = new DateInfoTool(objectMapper, fixedClock, mock(HttpClient.class));
        var diff = tool.resolveDateDiff(LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 15));
        assertEquals(3, diff.calendarDays());
        assertEquals(3, diff.absoluteCalendarDays());
    }

    @Test
    void dateDiff_negativeWhenEndBeforeStart() {
        DateInfoTool tool = new DateInfoTool(objectMapper, fixedClock, mock(HttpClient.class));
        var diff = tool.resolveDateDiff(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 12));
        assertEquals(-3, diff.calendarDays());
        assertEquals(3, diff.absoluteCalendarDays());
    }

    // --- Day classification: holiday API available ---

    @Test
    void publicHoliday() {
        var tool =
                toolWithHolidayApi("{\"code\":0,\"holiday\":{\"holiday\":true,\"name\":\"国庆节\"}}");
        var info = tool.resolve("2026-10-01", null, "Asia/Shanghai");
        assertEquals("2026-10-01", info.date());
        assertEquals("PUBLIC_HOLIDAY", info.dayType());
        assertEquals("国庆节", info.holidayName());
        assertTrue(info.holidayDataAvailable());
    }

    @Test
    void adjustedWorkday() {
        var tool =
                toolWithHolidayApi("{\"code\":0,\"holiday\":{\"holiday\":false,\"name\":\"调休\"}}");
        var info = tool.resolve("2026-10-10", null, "Asia/Shanghai");
        assertEquals("ADJUSTED_WORKDAY", info.dayType());
    }

    @Test
    void regularWorkday() {
        var tool =
                toolWithHolidayApi(
                        "{\"code\":0,\"holiday\":null}"); // API returns null holiday for regular
        // days
        var info = tool.resolve("2026-08-12", null, "Asia/Shanghai");
        assertEquals("WORKDAY", info.dayType());
        assertNull(info.holidayName());
        assertTrue(info.holidayDataAvailable());
    }

    @Test
    void weekend() {
        var tool = toolWithHolidayApi("{\"code\":0,\"holiday\":null}");
        var info = tool.resolve("2026-08-15", null, "Asia/Shanghai"); // Saturday
        assertEquals("WEEKEND", info.dayType());
    }

    // --- Holiday API unavailable → graceful degradation ---

    @Test
    @SuppressWarnings("unchecked")
    void holidayApiUnavailable_fallsBackToWeekdayClassification() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(503);

        var tool = new DateInfoTool(objectMapper, fixedClock, httpClient);
        var info = tool.resolve("2026-08-12", null, "Asia/Shanghai");

        assertFalse(info.holidayDataAvailable());
        assertEquals("WORKDAY", info.dayType());
        assertNull(info.holidayName());
    }

    // --- Resolve defaults to today when start_date omitted ---

    @Test
    void omittedStartDate_defaultsToToday() {
        var tool = toolWithHolidayApi("{\"code\":0,\"holiday\":null}");
        var info = tool.resolve(null, null, "Asia/Shanghai");
        assertEquals("2026-08-12", info.date()); // fixedClock is 2026-08-12
    }

    // --- Date diff integrated ---

    @Test
    void withEndDate_includesDiff() {
        var tool = toolWithHolidayApi("{\"code\":0,\"holiday\":null}");
        var info = tool.resolve("2026-08-12", "2026-08-15", "Asia/Shanghai");
        assertEquals(3, info.dateDiff().calendarDays());
    }

    // --- helper ---

    @SuppressWarnings("unchecked")
    private DateInfoTool toolWithHolidayApi(String jsonBody) {
        try {
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> mockResponse = mock(HttpResponse.class);
            when(httpClient.send(any(), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockResponse);
            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.body()).thenReturn(jsonBody);
            return new DateInfoTool(objectMapper, fixedClock, httpClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

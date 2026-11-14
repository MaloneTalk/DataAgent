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
package io.github.malonetalk.utils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Shared date parsing; storage-specific limits are explicit so other callers can use historical dates. */
public final class DateTimeUtils {
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                    .withResolverStyle(ResolverStyle.STRICT);

    private DateTimeUtils() {}

    public static LocalDate parseDate(String text) {
        return LocalDate.parse(text);
    }

    /** Accepts ISO local date-times and the database-style space separator without applying a timezone. */
    public static LocalDateTime parseDateTime(String text) {
        return text.indexOf('T') >= 0
                ? LocalDateTime.parse(text)
                : LocalDateTime.parse(text, DATE_TIME);
    }

    public static LocalDate parseMysqlDate(String text) {
        LocalDate date = parseDate(text);
        requireMysqlYear(date.getYear());
        return date;
    }

    /** CSV DATE/DATETIME storage supports years 1000–9999 and whole seconds. */
    public static LocalDateTime parseMysqlDateTime(String text) {
        LocalDateTime date = parseDateTime(text);
        requireMysqlYear(date.getYear());
        if (date.getNano() != 0)
            throw new DateTimeException("MySQL date-time must use whole seconds.");
        return date;
    }

    private static void requireMysqlYear(int year) {
        if (year < 1000 || year > 9999)
            throw new DateTimeException("Date is outside the MySQL range.");
    }
}

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

import java.util.regex.Pattern;

/** Reserved physical names shared by CSV task creation, table writes and orphan cleanup. */
public final class CsvPhysicalNames {
    private static final String TABLE_PREFIX = "csv_t_";
    private static final Pattern IDENTIFIER = Pattern.compile("[a-f0-9]{32}");
    private static final Pattern TABLE = Pattern.compile(TABLE_PREFIX + IDENTIFIER.pattern());

    private CsvPhysicalNames() {}

    public static String table(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches())
            throw new IllegalArgumentException("Invalid CSV table identifier.");
        return TABLE_PREFIX + identifier;
    }

    public static boolean isManagedTable(String name) {
        return TABLE.matcher(name).matches();
    }

    public static String column(int index) {
        return "c%03d".formatted(index + 1);
    }
}

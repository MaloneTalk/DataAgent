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

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The same alias rules are used when saving, matching and confirming a choice. */
public final class MetricAliasUtils {
    private MetricAliasUtils() {}

    public static String normalize(String value) {
        return display(value).replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static List<String> aliases(String value) {
        var distinct = new LinkedHashMap<String, String>();
        Arrays.stream(display(value).split("[,、;/|\\r\\n]+"))
                .map(MetricAliasUtils::display)
                .filter(alias -> !alias.isEmpty())
                .forEach(alias -> distinct.putIfAbsent(normalize(alias), alias));
        return List.copyOf(distinct.values());
    }

    public static String canonicalize(String value) {
        return String.join(", ", aliases(value));
    }

    private static String display(String value) {
        return Normalizer.normalize(Optional.ofNullable(value).orElse(""), Normalizer.Form.NFKC)
                .strip()
                .replaceAll("[\\t\\x0B\\f ]+", " ");
    }
}

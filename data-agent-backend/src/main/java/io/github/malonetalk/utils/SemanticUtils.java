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

import io.github.malonetalk.common.SemanticConstants;
import java.util.Locale;

public final class SemanticUtils {

    private SemanticUtils() {}

    public static void requireDatasourceId(Integer datasourceId) {
        if (datasourceId == null) {
            throw new IllegalArgumentException("datasourceId cannot be null.");
        }
    }

    public static void validateSortOrder(String sortOrder) {
        if (sortOrder == null) {
            return;
        }
        if (!SemanticConstants.SORT_ORDER_ASC.equalsIgnoreCase(sortOrder)
                && !SemanticConstants.SORT_ORDER_DESC.equalsIgnoreCase(sortOrder)) {
            throw new IllegalArgumentException("sortOrder must be asc or desc.");
        }
    }

    public static String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String requireName(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }

    public static String normalizeIdentifierKey(String value) {
        return requireName(value, "value").toLowerCase(Locale.ROOT);
    }

    public static boolean matchesKeywordPrefix(String value, String keywordPrefix) {
        if (keywordPrefix == null || keywordPrefix.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT)
                .startsWith(keywordPrefix.trim().toLowerCase(Locale.ROOT));
    }
}

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

import io.github.malonetalk.common.Constants;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.dto.prompt.ColumnPromptResponse;
import io.github.malonetalk.exception.BusinessException;
import java.util.List;
import java.util.Locale;

public final class SemanticUtils {

    private SemanticUtils() {}

    public static void requireDatasourceId(Integer datasourceId) {
        RequestAssert.requireNonNull(datasourceId, "datasourceId cannot be null.");
    }

    /**
     * @param sortOrder nullable, treated as asc
     * @return true if sortOrder is "desc" (case-insensitive)
     */
    public static boolean isDescendingSort(String sortOrder) {
        if (sortOrder == null) {
            return false;
        }
        if (Constants.SORT_ORDER_ASC.equalsIgnoreCase(sortOrder)) {
            return false;
        }
        if (Constants.SORT_ORDER_DESC.equalsIgnoreCase(sortOrder)) {
            return true;
        }
        throw BusinessException.of(
                ErrorCode.BAD_REQUEST, "sortOrder must be 'asc' or 'desc', but got: " + sortOrder);
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String requireTrimmed(String value, String missingMessage) {
        return RequestAssert.requireNotBlank(value, missingMessage);
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    public static String normalizeDomain(String domain) {
        String normalized = trimToNull(domain);
        return normalized == null
                ? SemanticConstants.DEFAULT_DOMAIN
                : normalized.toLowerCase(Locale.ROOT);
    }

    public static String normalizeObjectName(String value, String missingMessage) {
        return requireTrimmed(value, missingMessage).toLowerCase(Locale.ROOT);
    }

    public static boolean containsIgnoreCase(String value, String keyword) {
        String normalizedValue = trimToNull(value);
        String normalizedKeyword = trimToNull(keyword);
        return normalizedValue != null
                && normalizedKeyword != null
                && normalizedValue
                        .toLowerCase(Locale.ROOT)
                        .contains(normalizedKeyword.toLowerCase(Locale.ROOT));
    }

    public static String formatTableSchema(String tableName, List<ColumnPromptResponse> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Schema of table %s:%n", tableName));
        sb.append("  Columns:\n");
        for (ColumnPromptResponse col : columns) {
            String type = col.type() == null ? "UNKNOWN" : col.type();
            sb.append(String.format("    - %s (%s)", col.name(), type));
            if (Boolean.TRUE.equals(col.primaryKey())) {
                sb.append(" [PRIMARY KEY]");
            }
            if (col.indexInfo() != null) {
                sb.append(String.format(" [INDEX_PART: %s]", col.indexInfo()));
            }
            if (col.description() != null) {
                sb.append(String.format(" - %s", col.description()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

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
import io.github.malonetalk.dto.prompt.ColumnPromptResponse;
import java.util.List;
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

    /**
     * @return the original value (not trimmed), for chaining with {@code .trim()}
     */
    public static String checkNotBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
        return value;
    }

    public static String normalizeIdentifierKey(String value) {
        return checkNotBlank(value, "value").trim().toLowerCase(Locale.ROOT);
    }

    public static String formatTableSchema(String tableName, List<ColumnPromptResponse> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Schema of table %s:%n", tableName));
        sb.append("  Columns:\n");
        for (ColumnPromptResponse col : columns) {
            sb.append(String.format("    - %s (%s)", col.name(), col.type()));
            if (Boolean.TRUE.equals(col.primaryKey())) {
                sb.append(" [PRIMARY KEY]");
            }
            if (col.description() != null) {
                sb.append(String.format(" - %s", col.description()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

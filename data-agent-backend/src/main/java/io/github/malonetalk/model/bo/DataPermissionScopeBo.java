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
package io.github.malonetalk.model.bo;

import static io.github.malonetalk.utils.SemanticUtils.normalizeIdentifier;

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Permissions loaded once for one operation, without a cross-request cache. */
public record DataPermissionScopeBo(
        boolean unrestricted, Map<String, String> tables, Map<String, Set<String>> hiddenColumns) {
    public boolean canTable(String table) {
        return unrestricted || tables.containsKey(normalizeIdentifier(table));
    }

    public String requireTable(String table) {
        if (!canTable(table))
            throw BusinessException.of(ErrorCode.FORBIDDEN, "Table access denied.");
        return unrestricted ? table : tables.get(normalizeIdentifier(table));
    }

    public Set<String> hidden(String table) {
        return hiddenColumns.getOrDefault(normalizeIdentifier(table), Set.of());
    }

    public boolean canColumn(String table, String column) {
        return canTable(table) && !hidden(table).contains(normalizeIdentifier(column));
    }

    public boolean canColumns(String table, Collection<String> columns) {
        return canTable(table) && !hasHiddenColumns(table, columns);
    }

    public boolean hasHiddenColumns(String table, Collection<String> columns) {
        var hidden = hidden(table);
        return columns.stream().map(c -> normalizeIdentifier(c)).anyMatch(hidden::contains);
    }
}

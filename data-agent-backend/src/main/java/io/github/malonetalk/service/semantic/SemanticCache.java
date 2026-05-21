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
package io.github.malonetalk.service.semantic;

import static io.github.malonetalk.utils.SemanticStringUtils.normalizeName;

import io.github.malonetalk.entity.ResolvedColumn;
import io.github.malonetalk.entity.ResolvedRelation;
import io.github.malonetalk.entity.ResolvedTable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class SemanticCache {

    private List<ResolvedTable> tables;
    private final Map<String, ResolvedTable> tablesByKey = new HashMap<>();
    private final Map<String, List<ResolvedColumn>> columnsByTableKey = new HashMap<>();
    private final Map<String, Map<String, ResolvedColumn>> columnLookupByTableKey = new HashMap<>();
    private final Map<String, List<ResolvedRelation>> relationsByTableKey = new HashMap<>();

    public List<ResolvedTable> getOrComputeTables(Supplier<List<ResolvedTable>> supplier) {
        if (tables == null) {
            tables = supplier.get();
        }
        return tables;
    }

    public ResolvedTable getOrComputeTable(
            String tableKey, Function<String, ResolvedTable> compute) {
        return tablesByKey.computeIfAbsent(tableKey, compute);
    }

    public List<ResolvedColumn> findColumns(String tableKey) {
        return columnsByTableKey.get(tableKey);
    }

    public ResolvedColumn findColumn(String tableKey, String columnKey) {
        Map<String, ResolvedColumn> lookup = columnLookupByTableKey.get(tableKey);
        return lookup == null ? null : lookup.get(columnKey);
    }

    public void putColumns(
            String tableKey, String canonicalTableName, List<ResolvedColumn> columns) {
        Map<String, ResolvedColumn> lookup = new HashMap<>();
        for (ResolvedColumn column : columns) {
            lookup.put(normalizeName(column.columnName()), column);
        }
        columnsByTableKey.put(tableKey, columns);
        columnLookupByTableKey.put(tableKey, lookup);

        String canonicalTableKey = normalizeName(canonicalTableName);
        if (!canonicalTableKey.equals(tableKey)) {
            columnsByTableKey.put(canonicalTableKey, columns);
            columnLookupByTableKey.put(canonicalTableKey, lookup);
        }
    }

    public List<ResolvedRelation> findRelations(String tableKey) {
        return relationsByTableKey.get(tableKey);
    }

    public void putRelations(
            String tableKey, String canonicalTableName, List<ResolvedRelation> relations) {
        relationsByTableKey.put(tableKey, relations);
        String canonicalTableKey = normalizeName(canonicalTableName);
        if (!canonicalTableKey.equals(tableKey)) {
            relationsByTableKey.put(canonicalTableKey, relations);
        }
    }
}

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
package io.github.malonetalk.service.semantic.impl;

import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.service.semantic.SemanticService;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SemanticServiceImpl implements SemanticService {

    private final LogicalTableRelationHelper logicalTableRelationHelper;

    public SemanticServiceImpl(LogicalTableRelationHelper logicalTableRelationHelper) {
        this.logicalTableRelationHelper = logicalTableRelationHelper;
    }

    @Override
    public String normalizeIdentifierKey(String value) {
        return logicalTableRelationHelper.normalizeIdentifierKey(value);
    }

    @Override
    public Set<String> normalizeIdentifierKeys(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
        LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " contains a blank value.");
            }
            normalizedValues.add(normalizeIdentifierKey(value));
        }
        return normalizedValues;
    }

    @Override
    public TableInfo findSemanticTable(
            List<TableInfo> semanticTables, Integer datasourceId, String tableName) {
        String normalizedTableName = normalizeIdentifierKey(tableName);
        for (TableInfo semanticTable : semanticTables) {
            if (!datasourceId.equals(semanticTable.getDatasourceId())) {
                continue;
            }
            if (normalizeIdentifierKey(semanticTable.getTableName()).equals(normalizedTableName)) {
                return semanticTable;
            }
        }
        return null;
    }

    @Override
    public ColumnInfo findSemanticColumn(
            List<ColumnInfo> semanticColumns,
            Integer datasourceId,
            String tableName,
            String columnName) {
        String normalizedTableName = normalizeIdentifierKey(tableName);
        String normalizedColumnName = normalizeIdentifierKey(columnName);
        for (ColumnInfo semanticColumn : semanticColumns) {
            if (!datasourceId.equals(semanticColumn.getDatasourceId())) {
                continue;
            }
            if (!normalizeIdentifierKey(semanticColumn.getTableName())
                    .equals(normalizedTableName)) {
                continue;
            }
            if (normalizeIdentifierKey(semanticColumn.getColumnName())
                    .equals(normalizedColumnName)) {
                return semanticColumn;
            }
        }
        return null;
    }

    @Override
    public Map<String, TableInfo> toSemanticTableMap(List<TableInfo> semanticTables) {
        Map<String, TableInfo> semanticTablesByName = new LinkedHashMap<>();
        for (TableInfo semanticTable : semanticTables) {
            semanticTablesByName.put(
                    normalizeIdentifierKey(semanticTable.getTableName()), semanticTable);
        }
        return semanticTablesByName;
    }

    @Override
    public Map<String, ColumnInfo> toSemanticColumnMap(List<ColumnInfo> semanticColumns) {
        Map<String, ColumnInfo> semanticColumnsByName = new LinkedHashMap<>();
        for (ColumnInfo semanticColumn : semanticColumns) {
            semanticColumnsByName.put(
                    normalizeIdentifierKey(semanticColumn.getColumnName()), semanticColumn);
        }
        return semanticColumnsByName;
    }
}

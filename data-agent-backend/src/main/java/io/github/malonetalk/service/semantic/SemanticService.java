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

import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SemanticService {

    String normalizeIdentifierKey(String value);

    Set<String> normalizeIdentifierKeys(List<String> values, String fieldName);

    TableInfo findSemanticTable(
            List<TableInfo> semanticTables, Integer datasourceId, String tableName);

    ColumnInfo findSemanticColumn(
            List<ColumnInfo> semanticColumns,
            Integer datasourceId,
            String tableName,
            String columnName);

    Map<String, TableInfo> toSemanticTableMap(List<TableInfo> semanticTables);

    Map<String, ColumnInfo> toSemanticColumnMap(List<ColumnInfo> semanticColumns);
}

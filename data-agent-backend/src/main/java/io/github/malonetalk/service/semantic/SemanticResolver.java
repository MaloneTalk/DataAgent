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

import static io.github.malonetalk.utils.SemanticStringUtils.normalizeBlankToNull;

import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.ResolvedColumn;
import io.github.malonetalk.entity.ResolvedTable;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.service.semantic.SemanticManager.ColumnMergeSnapshot;
import io.github.malonetalk.service.semantic.SemanticManager.TableMergeSnapshot;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SemanticResolver {

    private final SemanticManager semanticManager;

    public SemanticResolver(SemanticManager semanticManager) {
        this.semanticManager = semanticManager;
    }

    public ResolvedTable resolveTable(TableMergeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        TableInfo physicalTable = snapshot.physicalTable();
        TableInfo semanticTable = snapshot.semanticTable();
        String physicalDescription =
                normalizeBlankToNull(
                        physicalTable != null ? physicalTable.getTableDescription() : null);
        String semanticDescription =
                normalizeBlankToNull(
                        semanticTable != null ? semanticTable.getTableDescription() : null);
        LocalDateTime updateTime =
                semanticTable != null
                        ? firstNonNull(semanticTable.getUpdateTime(), semanticTable.getCreateTime())
                        : null;
        return new ResolvedTable(
                semanticTable != null ? semanticTable.getId() : null,
                semanticManager.resolveTableName(snapshot),
                physicalTable,
                semanticTable,
                resolveDomain(physicalTable, semanticTable),
                semanticDescription != null ? semanticDescription : physicalDescription,
                physicalDescription,
                updateTime,
                semanticManager.isTableVisible(semanticTable, physicalTable));
    }

    public ResolvedColumn resolveColumn(String tableName, ColumnMergeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        io.github.malonetalk.agent.datasource.ColumnInfo physicalColumn = snapshot.physicalColumn();
        ColumnInfo semanticColumn = snapshot.semanticColumn();
        String physicalDescription =
                normalizeBlankToNull(physicalColumn != null ? physicalColumn.remarks() : null);
        String semanticDescription =
                normalizeBlankToNull(
                        semanticColumn != null ? semanticColumn.getColumnDescription() : null);
        LocalDateTime updateTime =
                semanticColumn != null
                        ? firstNonNull(
                                semanticColumn.getUpdateTime(), semanticColumn.getCreateTime())
                        : null;
        return new ResolvedColumn(
                semanticColumn != null ? semanticColumn.getId() : null,
                tableName,
                physicalColumn != null
                        ? physicalColumn.columnName()
                        : semanticColumn != null ? semanticColumn.getColumnName() : null,
                physicalColumn,
                semanticColumn,
                semanticDescription != null ? semanticDescription : physicalDescription,
                physicalDescription,
                physicalColumn != null ? physicalColumn.typeName() : null,
                buildTypeText(physicalColumn),
                physicalColumn != null && physicalColumn.primaryKey(),
                physicalColumn != null && physicalColumn.nullable(),
                normalizeBlankToNull(physicalColumn != null ? physicalColumn.defaultValue() : null),
                updateTime,
                semanticManager.isColumnVisible(semanticColumn, physicalColumn));
    }

    public String resolveCanonicalTableName(TableMergeSnapshot snapshot) {
        return semanticManager.resolveTableName(snapshot);
    }

    private String resolveDomain(TableInfo physicalTable, TableInfo semanticTable) {
        String semanticDomain =
                normalizeBlankToNull(semanticTable != null ? semanticTable.getDomain() : null);
        if (semanticDomain != null) {
            return semanticDomain;
        }
        return normalizeBlankToNull(physicalTable != null ? physicalTable.getDomain() : null);
    }

    private String buildTypeText(io.github.malonetalk.agent.datasource.ColumnInfo physicalColumn) {
        if (physicalColumn == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(physicalColumn.typeName());
        if (physicalColumn.columnSize() > 0) {
            sb.append("(").append(physicalColumn.columnSize()).append(")");
        }
        return sb.toString();
    }

    private LocalDateTime firstNonNull(LocalDateTime primary, LocalDateTime fallback) {
        return primary != null ? primary : fallback;
    }
}

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
package io.github.malonetalk.service.impl.semantic;

import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.SemanticSchemaException;
import io.github.malonetalk.service.semantic.SemanticManager;
import io.github.malonetalk.service.semantic.SemanticManager.ColumnMergeSnapshot;
import io.github.malonetalk.service.semantic.SemanticManager.TableMergeSnapshot;
import io.github.malonetalk.service.semantic.SemanticManager.VisibilityContext;
import io.github.malonetalk.service.semantic.SemanticSnapshotFactory;
import org.springframework.stereotype.Service;

@Service
public class SemanticSnapshotFactoryImpl implements SemanticSnapshotFactory {

    private final SemanticManager semanticManager;

    public SemanticSnapshotFactoryImpl(SemanticManager semanticManager) {
        this.semanticManager = semanticManager;
    }

    @Override
    public VisibilityContext createVisibilityContext(Datasource datasource) {
        return semanticManager.createVisibilityContext(datasource);
    }

    @Override
    public TableMergeSnapshot requireTableSnapshotOrThrow(Datasource datasource, String tableName) {
        return requireTableSnapshotOrThrow(createVisibilityContext(datasource), tableName);
    }

    @Override
    public TableMergeSnapshot requireTableSnapshotOrThrow(
            VisibilityContext visibilityContext, String tableName) {
        TableMergeSnapshot tableSnapshot = visibilityContext.findMergedTable(tableName);
        if (tableSnapshot == null) {
            throw new SemanticSchemaException("Table " + tableName + " does not exist.");
        }
        return tableSnapshot;
    }

    @Override
    public TableMergeSnapshot requirePhysicalTableSnapshotOrThrow(
            Datasource datasource, String tableName) {
        return requirePhysicalTableSnapshotOrThrow(createVisibilityContext(datasource), tableName);
    }

    @Override
    public TableMergeSnapshot requirePhysicalTableSnapshotOrThrow(
            VisibilityContext visibilityContext, String tableName) {
        TableMergeSnapshot tableSnapshot = requireTableSnapshotOrThrow(visibilityContext, tableName);
        if (tableSnapshot.physicalTable() == null) {
            throw new SemanticSchemaException("Table " + tableName + " does not exist.");
        }
        return tableSnapshot;
    }

    @Override
    public ColumnMergeSnapshot requirePhysicalColumnSnapshotOrThrow(
            VisibilityContext visibilityContext, String tableName, String columnName) {
        ColumnMergeSnapshot columnSnapshot =
                visibilityContext.findMergedColumn(tableName, columnName);
        if (columnSnapshot == null || columnSnapshot.physicalColumn() == null) {
            throw new SemanticSchemaException(
                    "Column " + tableName + "." + columnName + " does not exist.");
        }
        return columnSnapshot;
    }

    @Override
    public String resolveCanonicalTableName(TableMergeSnapshot snapshot) {
        return semanticManager.resolveTableName(snapshot);
    }
}

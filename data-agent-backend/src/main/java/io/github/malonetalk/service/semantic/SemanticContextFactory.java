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

import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import io.github.malonetalk.service.semantic.relation.RelationSemanticRepository;
import io.github.malonetalk.service.semantic.SemanticManager.VisibilityContext;
import org.springframework.stereotype.Component;

@Component
public class SemanticContextFactory {

    private final SemanticResolver semanticResolver;
    private final SemanticSnapshotFactory semanticSnapshotFactory;
    private final RelationSemanticRepository relationSemanticRepository;
    private final LogicalTableRelationHelper logicalTableRelationHelper;
    private final SchemaReader schemaReader;

    public SemanticContextFactory(
            SemanticResolver semanticResolver,
            SemanticSnapshotFactory semanticSnapshotFactory,
            RelationSemanticRepository relationSemanticRepository,
            LogicalTableRelationHelper logicalTableRelationHelper,
            SchemaReader schemaReader) {
        this.semanticResolver = semanticResolver;
        this.semanticSnapshotFactory = semanticSnapshotFactory;
        this.relationSemanticRepository = relationSemanticRepository;
        this.logicalTableRelationHelper = logicalTableRelationHelper;
        this.schemaReader = schemaReader;
    }

    public SemanticContext createContext(Datasource datasource) {
        return createContext(datasource, semanticSnapshotFactory.createVisibilityContext(datasource));
    }

    public SemanticContext createContext(
            Datasource datasource, VisibilityContext visibilityContext) {
        return new SemanticContext(
                datasource,
                visibilityContext,
                semanticResolver,
                relationSemanticRepository,
                logicalTableRelationHelper,
                schemaReader);
    }
}

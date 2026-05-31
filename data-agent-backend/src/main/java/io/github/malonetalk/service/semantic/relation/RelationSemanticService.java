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
package io.github.malonetalk.service.semantic.relation;

import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.BindLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.RelationSemanticPageQuery;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationRequest;
import java.util.List;

public interface RelationSemanticService {

    PageResponse<LogicalTableRelationResponse> getRelationPage(RelationSemanticPageQuery query);

    LogicalTableRelationResponse createRelationSemantic(
            Integer datasourceId, String tableName, BindLogicalTableRelationRequest request);

    LogicalTableRelationResponse updateRelationSemantic(
            Integer datasourceId,
            String tableName,
            Integer relationId,
            UpdateLogicalTableRelationRequest request);

    boolean updateRelationSemanticEnabled(
            Integer datasourceId, String tableName, Integer relationId, Boolean enabled);

    boolean deleteRelationSemantic(Integer datasourceId, String tableName, Integer relationId);

    int deleteRelationSemantics(Integer datasourceId, String tableName, List<Integer> relationIds);
}

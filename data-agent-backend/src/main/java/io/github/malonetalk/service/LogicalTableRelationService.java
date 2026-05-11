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
package io.github.malonetalk.service;

import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.BindLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateColumnResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateTableResponse;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationRequest;
import io.github.malonetalk.entity.LogicalTableRelation;
import java.util.List;

public interface LogicalTableRelationService {

    List<LogicalTableRelationResponse> listByDatasourceIdAndTable(
            Integer datasourceId, String tableName);

    PageResponse<LogicalTableRelationResponse> listByDatasourceIdAndTable(
            Integer datasourceId, String tableName, PageRequest pageRequest);

    LogicalTableRelationResponse create(
            Integer datasourceId, String tableName, BindLogicalTableRelationRequest request);

    LogicalTableRelationResponse update(
            Integer datasourceId,
            String tableName,
            Integer relationId,
            UpdateLogicalTableRelationRequest request);

    boolean updateEnabled(
            Integer datasourceId, String tableName, Integer relationId, Boolean enabled);

    boolean delete(Integer datasourceId, String tableName, Integer relationId);

    int deleteBatch(Integer datasourceId, String tableName, List<Integer> relationIds);

    PageResponse<RelationCandidateTableResponse> listCandidateTables(
            Integer datasourceId, PageRequest pageRequest);

    PageResponse<RelationCandidateColumnResponse> listCandidateColumns(
            Integer datasourceId, String tableName, PageRequest pageRequest);

    List<LogicalTableRelation> listEnabledByDatasourceIdAndSourceTable(
            Integer datasourceId, String tableName);

    int deleteByDatasourceId(Integer datasourceId);
}

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

import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.ResolvedTable;
import io.github.malonetalk.service.semantic.SemanticContext;
import java.util.List;

public interface RelationSemanticPolicyService {

    List<Integer> normalizeRelationIds(List<Integer> relationIds, String fieldName);

    String normalizeKeywordPrefix(String keywordPrefix);

    ResolvedTable requireVisibleTable(
            SemanticContext readContext, String tableName, String roleLabel);

    void requireVisibleColumns(
            SemanticContext readContext,
            String tableName,
            List<String> columnNames,
            String roleLabel);

    RelationDraft buildDraft(
            SemanticContext readContext,
            String tableName,
            List<String> sourceColumnNames,
            String targetTableName,
            List<String> targetColumnNames,
            String description,
            Boolean enabled);

    LogicalTableRelationResponse mapResponse(
            SemanticContext readContext, LogicalTableRelation relation);

    record RelationDraft(
            String sourceTableName,
            String sourceColumnNamesJson,
            String sourceColumnSignature,
            String targetTableName,
            String targetColumnNamesJson,
            String targetColumnSignature,
            String description,
            Boolean enabled) {}
}

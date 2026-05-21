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

import io.github.malonetalk.entity.LogicalTableRelation;
import java.time.LocalDateTime;
import java.util.List;

public interface RelationSemanticRepository {

    LogicalTableRelation findById(Integer id);

    List<LogicalTableRelation> listByDatasourceId(Integer datasourceId);

    List<LogicalTableRelation> listByDatasourceIdAndSourceTable(
            Integer datasourceId, String sourceTableName);

    long countByDatasourceIdAndSourceTable(
            Integer datasourceId, String sourceTableName, String keywordPrefix, Boolean enabled);

    List<LogicalTableRelation> listPageByDatasourceIdAndSourceTable(
            Integer datasourceId,
            String sourceTableName,
            String keywordPrefix,
            Boolean enabled,
            boolean sortDescending,
            long offset,
            int limit);

    List<LogicalTableRelation> listEnabledByDatasourceIdAndSourceTable(
            Integer datasourceId, String sourceTableName);

    LogicalTableRelation findByUniqueSourceKey(
            Integer datasourceId, String sourceTableName, String sourceColumnSignature);

    boolean save(LogicalTableRelation logicalTableRelation);

    boolean update(LogicalTableRelation logicalTableRelation);

    boolean updateEnabled(
            Integer id,
            Integer datasourceId,
            String sourceTableName,
            Boolean enabled,
            LocalDateTime updateTime);

    boolean deleteById(Integer id, Integer datasourceId, String sourceTableName);

    int deleteByIdsAndSourceTable(Integer datasourceId, String sourceTableName, List<Integer> ids);

    int deleteByIds(Integer datasourceId, List<Integer> ids);

    int deleteByDatasourceId(Integer datasourceId);
}

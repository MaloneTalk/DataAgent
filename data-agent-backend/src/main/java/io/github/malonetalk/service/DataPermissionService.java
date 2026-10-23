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

import static io.github.malonetalk.utils.SemanticUtils.normalizeIdentifier;

import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataPermissionService {
    private final RoleTablePermissionMapper tables;
    private final RoleHiddenColumnMapper columns;
    private final ColumnSemanticInfoMapper metadata;

    public boolean canTable(UserContextBo user, Integer datasourceId, String table) {
        return Boolean.TRUE.equals(user.superAdmin())
                || user.roleId() != null
                        && user.roleId() > 0
                        && tables.selectByRoleAndDatasource(user.roleId(), datasourceId).stream()
                                .anyMatch(
                                        p ->
                                                normalizeIdentifier(p.getTableName())
                                                        .equals(normalizeIdentifier(table)));
    }

    public void requireTable(UserContextBo user, Integer datasourceId, String table) {
        if (!canTable(user, datasourceId, table))
            throw BusinessException.of(ErrorCode.FORBIDDEN, "Table access denied.");
    }

    public boolean canDatasource(UserContextBo user, Integer datasourceId) {
        return Boolean.TRUE.equals(user.superAdmin())
                || user.roleId() != null
                        && user.roleId() > 0
                        && !tables.selectByRoleAndDatasource(user.roleId(), datasourceId).isEmpty();
    }

    public void requireDatasource(UserContextBo user, Integer datasourceId) {
        if (!canDatasource(user, datasourceId))
            throw BusinessException.of(ErrorCode.FORBIDDEN, "Datasource access denied.");
    }

    public Set<String> hidden(UserContextBo user, Integer datasourceId, String table) {
        if (Boolean.TRUE.equals(user.superAdmin())) return Set.of();
        return columns.selectByRoleAndDatasource(user.roleId(), datasourceId).stream()
                .filter(
                        p ->
                                normalizeIdentifier(p.getTableName())
                                        .equals(normalizeIdentifier(table)))
                .map(p -> normalizeIdentifier(p.getColumnName()))
                .collect(Collectors.toSet());
    }

    public boolean hasHiddenColumns(
            UserContextBo user, Integer datasourceId, String table, List<String> names) {
        Set<String> hidden = hidden(user, datasourceId, table);
        return names.stream().map(SemanticUtils::normalizeIdentifier).anyMatch(hidden::contains);
    }

    public List<ColumnInfo> visibleColumns(UserContextBo user, Integer datasourceId, String table) {
        requireTable(user, datasourceId, table);
        Set<String> hidden = hidden(user, datasourceId, table);
        return metadata.selectByDatasourceIdAndTableName(datasourceId, table).stream()
                .filter(c -> !hidden.contains(normalizeIdentifier(c.getColumnName())))
                .toList();
    }

    public Optional<Integer> roleScope(UserContextBo user) {
        return Boolean.TRUE.equals(user.superAdmin())
                ? Optional.empty()
                : Optional.ofNullable(user.roleId()).or(() -> Optional.of(0));
    }
}

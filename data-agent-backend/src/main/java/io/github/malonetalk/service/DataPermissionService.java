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

import io.github.malonetalk.entity.RoleTablePermission;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import java.util.Map;
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

    public boolean canTable(UserContextBo user, Integer datasourceId, String table) {
        return authorizedTableName(user, datasourceId, table).isPresent();
    }

    /** Returns the granted physical name, never the client-supplied spelling. */
    public String requireTable(UserContextBo user, Integer datasourceId, String table) {
        return authorizedTableName(user, datasourceId, table)
                .orElseThrow(
                        () -> BusinessException.of(ErrorCode.FORBIDDEN, "Table access denied."));
    }

    private Optional<String> authorizedTableName(
            UserContextBo user, Integer datasourceId, String table) {
        if (Boolean.TRUE.equals(user.superAdmin())) return Optional.of(table);
        if (user.roleId() == null || user.roleId() <= 0) return Optional.empty();
        return tables.selectByRoleAndDatasource(user.roleId(), datasourceId).stream()
                .map(RoleTablePermission::getTableName)
                .filter(name -> normalizeIdentifier(name).equals(normalizeIdentifier(table)))
                .findFirst();
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

    public Map<String, Set<String>> hiddenByTable(UserContextBo user, Integer datasourceId) {
        if (Boolean.TRUE.equals(user.superAdmin())) return Map.of();
        return columns.selectByRoleAndDatasource(user.roleId(), datasourceId).stream()
                .collect(
                        Collectors.groupingBy(
                                p -> normalizeIdentifier(p.getTableName()),
                                Collectors.mapping(
                                        p -> normalizeIdentifier(p.getColumnName()),
                                        Collectors.toSet())));
    }

    public Set<String> hidden(UserContextBo user, Integer datasourceId, String table) {
        return hiddenByTable(user, datasourceId).getOrDefault(normalizeIdentifier(table), Set.of());
    }

    public boolean hasHiddenColumns(
            UserContextBo user, Integer datasourceId, String table, List<String> names) {
        Set<String> hidden = hidden(user, datasourceId, table);
        return names.stream().map(SemanticUtils::normalizeIdentifier).anyMatch(hidden::contains);
    }

    public Optional<Integer> roleScope(UserContextBo user) {
        return Boolean.TRUE.equals(user.superAdmin())
                ? Optional.empty()
                : Optional.ofNullable(user.roleId()).or(() -> Optional.of(0));
    }
}

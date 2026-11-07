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

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.DatasourceMapper;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.model.bo.DataPermissionScopeBo;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.po.RoleTablePermission;
import io.github.malonetalk.service.csv.CsvAccessService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DataPermissionService {
    private final RoleTablePermissionMapper tables;
    private final RoleHiddenColumnMapper columns;
    private final DatasourceMapper datasources;
    private final CsvAccessService csvAccess;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DataPermissionScopeBo loadScope(UserContextBo user, Integer datasourceId) {
        return Optional.ofNullable(datasources.selectById(datasourceId))
                .map(
                        datasource ->
                                csvAccess.restrict(
                                        datasource, loadGrantedScope(user, datasourceId)))
                .orElseGet(() -> new DataPermissionScopeBo(false, Map.of(), Map.of()));
    }

    /** Role grants for management operations; analysis must use loadScope to enforce CSV readiness. */
    public DataPermissionScopeBo loadGrantedScope(UserContextBo user, Integer datasourceId) {
        if (Boolean.TRUE.equals(user.superAdmin()))
            return new DataPermissionScopeBo(true, Map.of(), Map.of());
        if (user.roleId() == null || user.roleId() <= 0)
            return new DataPermissionScopeBo(false, Map.of(), Map.of());
        var granted =
                tables.selectByRoleAndDatasource(user.roleId(), datasourceId).stream()
                        .collect(
                                Collectors.toMap(
                                        p -> normalizeIdentifier(p.getTableName()),
                                        RoleTablePermission::getTableName));
        return new DataPermissionScopeBo(false, granted, hiddenByTable(user, datasourceId));
    }

    /** Returns the granted physical name, never the client-supplied spelling. */
    public String requireTable(UserContextBo user, Integer datasourceId, String table) {
        return loadScope(user, datasourceId).requireTable(table);
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

    private Map<String, Set<String>> hiddenByTable(UserContextBo user, Integer datasourceId) {
        if (Boolean.TRUE.equals(user.superAdmin())) return Map.of();
        return columns.selectByRoleAndDatasource(user.roleId(), datasourceId).stream()
                .collect(
                        Collectors.groupingBy(
                                p -> normalizeIdentifier(p.getTableName()),
                                Collectors.mapping(
                                        p -> normalizeIdentifier(p.getColumnName()),
                                        Collectors.toSet())));
    }

    public Optional<Integer> roleScope(UserContextBo user) {
        return Boolean.TRUE.equals(user.superAdmin())
                ? Optional.empty()
                : Optional.ofNullable(user.roleId()).or(() -> Optional.of(0));
    }
}

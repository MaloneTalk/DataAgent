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

import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataPermissionService {
    private final PermissionService permissions;
    private final RoleTablePermissionMapper tables;
    private final RoleHiddenColumnMapper columns;
    private final ColumnSemanticInfoMapper metadata;

    public static String normalize(String name) {
        return name.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
    }

    public boolean canTable(UserContextBo user, Integer datasourceId, String table) {
        return Boolean.TRUE.equals(user.superAdmin())
                || user.roleId() != null
                        && user.roleId() > 0
                        && tables.selectByRoleAndDatasource(user.roleId(), datasourceId).stream()
                                .anyMatch(
                                        p -> normalize(p.getTableName()).equals(normalize(table)));
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

    public void requireDatasource(Integer userId, Integer datasourceId) {
        if (!canDatasource(permissions.user(userId), datasourceId))
            throw BusinessException.of(ErrorCode.FORBIDDEN, "Datasource access denied.");
    }

    public Set<String> hidden(UserContextBo user, Integer datasourceId, String table) {
        if (Boolean.TRUE.equals(user.superAdmin())) return Set.of();
        return columns.selectByRoleAndDatasource(user.roleId(), datasourceId).stream()
                .filter(p -> normalize(p.getTableName()).equals(normalize(table)))
                .map(p -> normalize(p.getColumnName()))
                .collect(Collectors.toSet());
    }

    public List<ColumnInfo> visibleColumns(UserContextBo user, Integer datasourceId, String table) {
        requireTable(user, datasourceId, table);
        Set<String> hidden = hidden(user, datasourceId, table);
        return metadata.selectByDatasourceIdAndTableName(datasourceId, table).stream()
                .filter(c -> !hidden.contains(normalize(c.getColumnName())))
                .toList();
    }

    public Optional<Integer> roleScope(UserContextBo user) {
        return Boolean.TRUE.equals(user.superAdmin())
                ? Optional.empty()
                : Optional.ofNullable(user.roleId()).or(() -> Optional.of(0));
    }
}

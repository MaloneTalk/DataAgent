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
package io.github.malonetalk.agent.tools;

import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.PermissionService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the datasource selected for one tool call and re-checks live permissions. */
@Component
@RequiredArgsConstructor
public class ToolDatasourceResolver {

    private final PermissionService permissions;
    private final DataPermissionService dataPermissions;
    private final DatasourceService datasources;

    public DatasourcePo resolve(ToolCallContext ctx, Integer requestedDatasourceId) {
        Integer datasourceId = requestedDatasourceId;
        if (datasourceId == null && ctx.datasourceIds().size() == 1) {
            datasourceId = ctx.datasourceIds().get(0);
        }
        if (datasourceId == null) {
            throw BusinessException.of(
                    ErrorCode.BAD_REQUEST,
                    "Multiple datasources are available. Specify datasource_id,"
                            + " or call get_datasources first.");
        }
        if (!ctx.datasourceIds().contains(datasourceId)) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "Datasource access denied.");
        }

        var user = permissions.user(ctx.userId());
        dataPermissions.requireDatasource(user, datasourceId);
        DatasourcePo datasource = datasources.requireById(datasourceId);
        if (!Status.ACTIVE.getCode().equals(datasource.getStatus())) {
            throw BusinessException.of(
                    ErrorCode.BAD_REQUEST, "Datasource is not active: " + datasourceId);
        }
        return datasource;
    }

    public List<DatasourcePo> accessible(ToolCallContext ctx) {
        var user = permissions.user(ctx.userId());
        Set<Integer> accessible = dataPermissions.accessibleDatasourceIds(user);
        Set<Integer> allowed = new HashSet<>(ctx.datasourceIds());
        allowed.retainAll(accessible);
        return datasources.findActiveByIds(allowed);
    }
}

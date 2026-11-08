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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import io.github.malonetalk.dto.SessionDatasourceBinding;
import io.github.malonetalk.mapper.SessionDatasourceMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.model.po.SessionDatasourcePo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Session datasource scope persistence, using MyBatis-Plus batch insert for audit fill. */
@Service
@RequiredArgsConstructor
public class SessionDatasourceService
        extends ServiceImpl<SessionDatasourceMapper, SessionDatasourcePo> {

    private final UserContextHolder userContextHolder;
    private final DatasourceService datasourceService;

    @Transactional
    public void replaceSessionDatasources(
            String sessionId, List<Integer> datasourceIds, UserContextBo user) {
        UserContextBo previous = userContextHolder.get();
        userContextHolder.set(user);
        try {
            remove(
                    Wrappers.<SessionDatasourcePo>lambdaQuery()
                            .eq(SessionDatasourcePo::getSessionId, sessionId));
            if (!datasourceIds.isEmpty()) {
                List<SessionDatasourcePo> rows =
                        datasourceIds.stream()
                                .map(
                                        datasourceId -> {
                                            SessionDatasourcePo row = new SessionDatasourcePo();
                                            row.setSessionId(sessionId);
                                            row.setDatasourceId(datasourceId);
                                            return row;
                                        })
                                .toList();
                saveBatch(rows);
            }
        } finally {
            if (previous == null) {
                userContextHolder.clear();
            } else {
                userContextHolder.set(previous);
            }
        }
    }

    public void deleteBySessionId(String sessionId) {
        remove(
                Wrappers.<SessionDatasourcePo>lambdaQuery()
                        .eq(SessionDatasourcePo::getSessionId, sessionId));
    }

    public List<SessionDatasourceBinding> listBindingsWithDatasourceName() {
        Map<Integer, String> names = new HashMap<>();
        for (DatasourcePo datasource : datasourceService.findAll()) {
            names.put(datasource.getId(), datasource.getName());
        }
        return list(
                        Wrappers.<SessionDatasourcePo>lambdaQuery()
                                .select(
                                        SessionDatasourcePo::getSessionId,
                                        SessionDatasourcePo::getDatasourceId))
                .stream()
                .map(
                        row -> {
                            SessionDatasourceBinding binding = new SessionDatasourceBinding();
                            binding.setSessionId(row.getSessionId());
                            binding.setDatasourceId(row.getDatasourceId());
                            binding.setDatasourceName(names.get(row.getDatasourceId()));
                            return binding;
                        })
                .distinct()
                .toList();
    }
}

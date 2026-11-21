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
package io.github.malonetalk.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.model.po.MetricClarificationPo;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MetricClarificationMapper extends AuditableMapper<MetricClarificationPo> {
    default Optional<MetricClarificationPo> find(Integer userId, String sessionId, boolean lock) {
        var query =
                Wrappers.<MetricClarificationPo>lambdaQuery()
                        .eq(MetricClarificationPo::getUserId, userId)
                        .eq(MetricClarificationPo::getSessionId, sessionId);
        if (lock) query.last("FOR UPDATE");
        return Optional.ofNullable(selectOne(query));
    }
}

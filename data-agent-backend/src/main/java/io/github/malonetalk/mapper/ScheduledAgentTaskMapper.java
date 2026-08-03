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

import io.github.malonetalk.entity.ScheduledAgentTask;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ScheduledAgentTaskMapper {

    int insert(ScheduledAgentTask task);

    int update(ScheduledAgentTask task);

    int updateEnabled(
            @Param("id") Integer id,
            @Param("enabled") boolean enabled,
            @Param("updateTime") LocalDateTime updateTime);

    int deleteById(@Param("id") Integer id);

    ScheduledAgentTask selectById(@Param("id") Integer id);

    List<ScheduledAgentTask> selectAll();

    List<ScheduledAgentTask> findDueTasks(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    int lockForRun(
            @Param("id") Integer id,
            @Param("now") LocalDateTime now,
            @Param("lockUntil") LocalDateTime lockUntil,
            @Param("force") boolean force);

    int finishRun(
            @Param("id") Integer id,
            @Param("nextRunAt") LocalDateTime nextRunAt,
            @Param("lastRunAt") LocalDateTime lastRunAt,
            @Param("lastStatus") String lastStatus,
            @Param("lastError") String lastError);
}

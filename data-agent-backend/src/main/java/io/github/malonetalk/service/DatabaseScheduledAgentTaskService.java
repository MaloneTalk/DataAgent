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

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.dto.ScheduledAgentTaskRequest;
import io.github.malonetalk.dto.ScheduledAgentTaskResponse;
import io.github.malonetalk.entity.ScheduledAgentTask;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.ScheduledAgentTaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DatabaseScheduledAgentTaskService implements ScheduledAgentTaskService {

    private final ScheduledAgentTaskMapper taskMapper;
    private final DatabaseScheduledAgentTaskRunner taskRunner;

    @Override
    public void create(ScheduledAgentTaskRequest request) {
        ScheduledAgentTask task = buildTask(request);
        taskMapper.insert(task);
    }

    @Override
    public void update(Integer id, ScheduledAgentTaskRequest request) {
        ScheduledAgentTask task = buildTask(request);
        task.setId(id);
        if (taskMapper.update(task) == 0) {
            throw BusinessException.of(
                    ErrorCode.DATA_CONFLICT,
                    "Scheduled task does not exist or is running: id=" + id);
        }
    }

    @Override
    public void delete(Integer id) {
        if (taskMapper.deleteById(id) == 0) {
            throw notFound(id);
        }
    }

    @Override
    public void setEnabled(Integer id, boolean enabled) {
        LocalDateTime nextRunAt = null;
        if (enabled) {
            ScheduledAgentTask task = requireTask(id);
            nextRunAt =
                    ScheduledAgentScheduleCalculator.nextRunAfter(
                            task.getScheduleType(), task.getScheduleExpr(), LocalDateTime.now());
        }
        updateEnabled(id, enabled, nextRunAt);
    }

    @Override
    public List<ScheduledAgentTaskResponse> listAll() {
        return taskMapper.selectAll().stream().map(ScheduledAgentTaskResponse::from).toList();
    }

    @Override
    public boolean runNow(Integer taskId) {
        return taskRunner.runNow(taskId);
    }

    private void updateEnabled(Integer id, boolean enabled, LocalDateTime nextRunAt) {
        if (taskMapper.updateEnabled(id, enabled, nextRunAt) == 0) {
            throw notFound(id);
        }
    }

    private ScheduledAgentTask requireTask(Integer id) {
        ScheduledAgentTask task = taskMapper.selectById(id);
        if (task == null) {
            throw notFound(id);
        }
        return task;
    }

    private ScheduledAgentTask buildTask(ScheduledAgentTaskRequest request) {
        ScheduledAgentTask task = new ScheduledAgentTask();
        task.setName(request.name().trim());
        task.setPrompt(request.prompt().trim());
        task.setScheduleType(request.scheduleType().name());
        task.setScheduleExpr(request.scheduleExpr().trim());
        task.setEnabled(request.enabled() == null || request.enabled());
        task.setNextRunAt(
                Boolean.TRUE.equals(task.getEnabled())
                        ? ScheduledAgentScheduleCalculator.nextRunAfter(
                                task.getScheduleType(), task.getScheduleExpr(), LocalDateTime.now())
                        : null);
        return task;
    }

    private BusinessException notFound(Integer id) {
        return BusinessException.of(
                ErrorCode.RESOURCE_NOT_FOUND, "Scheduled task does not exist: id=" + id);
    }
}

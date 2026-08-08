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
class DatabaseScheduledAgentTaskScheduler implements ScheduledAgentTaskScheduler {

    private final ScheduledAgentTaskMapper taskMapper;
    private final ScheduledAgentTaskExecutor taskExecutor;

    @Override
    public boolean create(ScheduledAgentTaskRequest request) {
        ScheduledAgentTask task = buildTask(new ScheduledAgentTask(), request);
        taskMapper.insert(task);
        return true;
    }

    @Override
    public boolean update(Integer id, ScheduledAgentTaskRequest request) {
        ScheduledAgentTask task = buildTask(new ScheduledAgentTask(), request);
        task.setId(id);
        if (taskMapper.update(task) == 0) {
            throw notFound(id);
        }
        return true;
    }

    @Override
    public boolean delete(Integer id) {
        if (taskMapper.deleteById(id) == 0) {
            throw notFound(id);
        }
        return true;
    }

    @Override
    public boolean activate(Integer id) {
        ScheduledAgentTask task = requireTask(id);
        LocalDateTime nextRunAt =
                ScheduledAgentScheduleCalculator.nextRunAfter(
                        task.getScheduleType(), task.getScheduleExpr(), LocalDateTime.now());
        return updateEnabled(id, true, nextRunAt);
    }

    @Override
    public boolean deactivate(Integer id) {
        return updateEnabled(id, false, null);
    }

    @Override
    public ScheduledAgentTaskResponse getStatus(Integer id) {
        return ScheduledAgentTaskResponse.from(requireTask(id));
    }

    @Override
    public List<ScheduledAgentTaskResponse> listAll() {
        return taskMapper.selectAll().stream().map(ScheduledAgentTaskResponse::from).toList();
    }

    @Override
    public boolean runNow(Integer taskId) {
        return taskExecutor.runNow(taskId);
    }

    private boolean updateEnabled(Integer id, boolean enabled, LocalDateTime nextRunAt) {
        if (taskMapper.updateEnabled(id, enabled, nextRunAt) == 0) {
            throw notFound(id);
        }
        return true;
    }

    private ScheduledAgentTask requireTask(Integer id) {
        ScheduledAgentTask task = taskMapper.selectById(id);
        if (task == null) {
            throw notFound(id);
        }
        return task;
    }

    private ScheduledAgentTask buildTask(
            ScheduledAgentTask task, ScheduledAgentTaskRequest request) {
        task.setName(request.name().trim());
        task.setPrompt(request.prompt().trim());
        task.setScheduleType(request.scheduleType().name());
        task.setScheduleExpr(request.scheduleExpr().trim());
        task.setEnabled(request.enabled() == null || request.enabled());
        task.setNextRunAt(
                ScheduledAgentScheduleCalculator.nextRunAfter(
                        task.getScheduleType(), task.getScheduleExpr(), LocalDateTime.now()));
        return task;
    }

    private BusinessException notFound(Integer id) {
        return BusinessException.of(
                ErrorCode.RESOURCE_NOT_FOUND, "Scheduled task does not exist: id=" + id);
    }
}

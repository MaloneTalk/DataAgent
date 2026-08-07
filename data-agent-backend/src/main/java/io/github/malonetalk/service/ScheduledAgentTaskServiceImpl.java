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
import io.github.malonetalk.enums.ScheduledAgentScheduleType;
import io.github.malonetalk.enums.ScheduledAgentTaskStatus;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.ScheduledAgentTaskMapper;
import io.github.malonetalk.utils.RequestAssert;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduledAgentTaskServiceImpl {

    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    private final ScheduledAgentTaskMapper taskMapper;
    private final ScheduledAgentScheduleCalculator scheduleCalculator;
    private final DatabasePollingScheduledAgentTaskScheduler taskScheduler;

    public ScheduledAgentTaskResponse create(ScheduledAgentTaskRequest request) {
        ScheduledAgentTask task = buildTask(new ScheduledAgentTask(), request);
        LocalDateTime now = LocalDateTime.now();
        task.setNextRunAt(
                scheduleCalculator.nextRunAfter(
                        task.getScheduleType(), task.getScheduleExpr(), now, task.getTimezone()));
        task.setCreateTime(now);
        task.setUpdateTime(now);
        taskMapper.insert(task);
        return toResponse(task);
    }

    public ScheduledAgentTaskResponse update(Integer id, ScheduledAgentTaskRequest request) {
        ScheduledAgentTask existing = getTask(id);
        ScheduledAgentTask task = buildTask(existing, request);
        task.setNextRunAt(
                scheduleCalculator.nextRunAfter(
                        task.getScheduleType(),
                        task.getScheduleExpr(),
                        LocalDateTime.now(),
                        task.getTimezone()));
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.update(task);
        ScheduledAgentTask saved = taskMapper.selectById(id);
        return toResponse(saved);
    }

    public void delete(Integer id) {
        RequestAssert.requireNonNegative(id, "id must be non-negative.");
        if (taskMapper.deleteById(id) == 0) {
            throw notFound(id);
        }
    }

    public ScheduledAgentTaskResponse getById(Integer id) {
        return toResponse(getTask(id));
    }

    public List<ScheduledAgentTaskResponse> listAll() {
        return taskMapper.selectAll().stream().map(this::toResponse).toList();
    }

    public void updateEnabled(Integer id, boolean enabled) {
        RequestAssert.requireNonNegative(id, "id must be non-negative.");
        if (taskMapper.updateEnabled(id, enabled, LocalDateTime.now()) == 0) {
            throw notFound(id);
        }
    }

    public boolean runNow(Integer id) {
        return taskScheduler.runNow(id);
    }

    private ScheduledAgentTask buildTask(
            ScheduledAgentTask task, ScheduledAgentTaskRequest request) {
        String timezone =
                request.timezone() == null || request.timezone().isBlank()
                        ? DEFAULT_TIMEZONE
                        : request.timezone().trim();
        timezone = scheduleCalculator.normalizeTimezone(timezone);

        ScheduledAgentScheduleType scheduleType = request.scheduleType();

        task.setName(RequestAssert.requireNotBlank(request.name(), "name cannot be blank."));
        task.setPrompt(RequestAssert.requireNotBlank(request.prompt(), "prompt cannot be blank."));
        task.setScheduleType(scheduleType.name());
        task.setScheduleExpr(
                RequestAssert.requireNotBlank(
                        request.scheduleExpr(), "scheduleExpr cannot be blank."));
        task.setTimezone(timezone);
        task.setEnabled(request.enabled() == null || request.enabled());
        return task;
    }

    private ScheduledAgentTask getTask(Integer id) {
        RequestAssert.requireNonNegative(id, "id must be non-negative.");
        ScheduledAgentTask task = taskMapper.selectById(id);
        if (task == null) {
            throw notFound(id);
        }
        return task;
    }

    private BusinessException notFound(Integer id) {
        return BusinessException.of(
                ErrorCode.RESOURCE_NOT_FOUND, "Scheduled task does not exist: id=" + id);
    }

    private ScheduledAgentTaskResponse toResponse(ScheduledAgentTask task) {
        return new ScheduledAgentTaskResponse(
                task.getId(),
                task.getName(),
                task.getPrompt(),
                ScheduledAgentScheduleType.valueOf(task.getScheduleType()),
                task.getScheduleExpr(),
                task.getTimezone(),
                task.getEnabled(),
                task.getNextRunAt(),
                toStatus(task.getLastStatus()),
                task.getCreateTime(),
                task.getUpdateTime());
    }

    private ScheduledAgentTaskStatus toStatus(String status) {
        return status == null ? null : ScheduledAgentTaskStatus.valueOf(status);
    }
}

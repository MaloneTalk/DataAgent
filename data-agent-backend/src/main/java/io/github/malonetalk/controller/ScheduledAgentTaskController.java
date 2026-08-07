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
package io.github.malonetalk.controller;

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.Result;
import io.github.malonetalk.dto.ScheduledAgentTaskRequest;
import io.github.malonetalk.dto.ScheduledAgentTaskResponse;
import io.github.malonetalk.entity.ScheduledAgentTask;
import io.github.malonetalk.enums.ScheduledAgentScheduleType;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.ScheduledAgentTaskMapper;
import io.github.malonetalk.service.DatabasePollingScheduledAgentTaskScheduler;
import io.github.malonetalk.service.ScheduledAgentScheduleCalculator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/scheduled-agent-tasks")
public class ScheduledAgentTaskController {

    private final ScheduledAgentTaskMapper taskMapper;
    private final ScheduledAgentScheduleCalculator scheduleCalculator;
    private final DatabasePollingScheduledAgentTaskScheduler taskScheduler;

    @PostMapping
    public Result<Boolean> create(@Valid @RequestBody ScheduledAgentTaskRequest request) {
        ScheduledAgentTask task = buildTask(new ScheduledAgentTask(), request);
        task.setNextRunAt(
                scheduleCalculator.nextRunAfter(
                        task.getScheduleType(), task.getScheduleExpr(), LocalDateTime.now()));
        taskMapper.insert(task);
        return Result.success(true);
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(
            @PathVariable @Positive(message = "id must be positive.") Integer id,
            @Valid @RequestBody ScheduledAgentTaskRequest request) {
        ScheduledAgentTask task = buildTask(new ScheduledAgentTask(), request);
        task.setId(id);
        task.setNextRunAt(
                scheduleCalculator.nextRunAfter(
                        task.getScheduleType(), task.getScheduleExpr(), LocalDateTime.now()));
        if (taskMapper.update(task) == 0) {
            throw notFound(id);
        }
        return Result.success(true);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(
            @PathVariable @Positive(message = "id must be positive.") Integer id) {
        if (taskMapper.deleteById(id) == 0) {
            throw notFound(id);
        }
        return Result.success(true);
    }

    @GetMapping
    public Result<List<ScheduledAgentTaskResponse>> listAll() {
        return Result.success(taskMapper.selectAll().stream().map(this::toResponse).toList());
    }

    @PostMapping("/{id}/run")
    public Result<Boolean> runNow(
            @PathVariable @Positive(message = "id must be positive.") Integer id) {
        return Result.success(taskScheduler.runNow(id));
    }

    private ScheduledAgentTask buildTask(
            ScheduledAgentTask task, ScheduledAgentTaskRequest request) {
        task.setName(request.name().trim());
        task.setPrompt(request.prompt().trim());
        task.setScheduleType(request.scheduleType().name());
        task.setScheduleExpr(request.scheduleExpr().trim());
        task.setEnabled(request.enabled() == null || request.enabled());
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
                task.getEnabled(),
                task.getNextRunAt());
    }
}

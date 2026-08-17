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

import io.github.malonetalk.common.Result;
import io.github.malonetalk.dto.ScheduledAgentTaskRequest;
import io.github.malonetalk.dto.ScheduledAgentTaskResponse;
import io.github.malonetalk.service.ScheduledAgentTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    private final ScheduledAgentTaskService taskService;

    @PostMapping
    public Result<Void> create(@Valid @RequestBody ScheduledAgentTaskRequest request) {
        taskService.create(request);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(
            @PathVariable @Positive(message = "id must be positive.") Integer id,
            @Valid @RequestBody ScheduledAgentTaskRequest request) {
        taskService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable @Positive(message = "id must be positive.") Integer id) {
        taskService.delete(id);
        return Result.success();
    }

    @GetMapping
    public Result<List<ScheduledAgentTaskResponse>> listAll() {
        return Result.success(taskService.listAll());
    }

    @PatchMapping("/{id}/enabled")
    public Result<Void> setEnabled(
            @PathVariable @Positive(message = "id must be positive.") Integer id,
            @Valid @RequestBody EnabledRequest request) {
        taskService.setEnabled(id, request.enabled());
        return Result.success();
    }

    @PostMapping("/{id}/run")
    public Result<Boolean> runNow(
            @PathVariable @Positive(message = "id must be positive.") Integer id) {
        return Result.success(taskService.runNow(id));
    }

    private record EnabledRequest(@NotNull(message = "enabled cannot be null.") Boolean enabled) {}
}

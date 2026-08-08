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
import io.github.malonetalk.service.ScheduledAgentTaskScheduler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

    private final ScheduledAgentTaskScheduler taskScheduler;

    @PostMapping
    public Result<Boolean> create(@Valid @RequestBody ScheduledAgentTaskRequest request) {
        return Result.success(taskScheduler.create(request));
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(
            @PathVariable @Positive(message = "id must be positive.") Integer id,
            @Valid @RequestBody ScheduledAgentTaskRequest request) {
        return Result.success(taskScheduler.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(
            @PathVariable @Positive(message = "id must be positive.") Integer id) {
        return Result.success(taskScheduler.delete(id));
    }

    @GetMapping
    public Result<List<ScheduledAgentTaskResponse>> listAll() {
        return Result.success(taskScheduler.listAll());
    }

    @GetMapping("/{id}/status")
    public Result<ScheduledAgentTaskResponse> getStatus(
            @PathVariable @Positive(message = "id must be positive.") Integer id) {
        return Result.success(taskScheduler.getStatus(id));
    }

    @PostMapping("/{id}/activate")
    public Result<Boolean> activate(
            @PathVariable @Positive(message = "id must be positive.") Integer id) {
        return Result.success(taskScheduler.activate(id));
    }

    @PostMapping("/{id}/deactivate")
    public Result<Boolean> deactivate(
            @PathVariable @Positive(message = "id must be positive.") Integer id) {
        return Result.success(taskScheduler.deactivate(id));
    }

    @PostMapping("/{id}/run")
    public Result<Boolean> runNow(
            @PathVariable @Positive(message = "id must be positive.") Integer id) {
        return Result.success(taskScheduler.runNow(id));
    }
}

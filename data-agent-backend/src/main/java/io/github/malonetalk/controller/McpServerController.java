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
import io.github.malonetalk.convertor.McpServerConverter;
import io.github.malonetalk.dto.McpServerRequest;
import io.github.malonetalk.dto.McpServerResponse;
import io.github.malonetalk.entity.McpServer;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.service.McpServerService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/mcp-server")
public class McpServerController {

    private final McpServerService mcpServerService;
    private final McpServerConverter mcpServerConverter;

    @GetMapping
    public Result<List<McpServerResponse>> findAll() {
        return Result.success(toResponses(mcpServerService.findAll()));
    }

    @GetMapping("/{id}")
    public Result<McpServerResponse> findById(@PathVariable Integer id) {
        McpServer mcpServer = mcpServerService.findById(id);
        if (mcpServer == null) {
            return Result.error(404, "McpServer not found");
        }
        return Result.success(mcpServerConverter.toResponse(mcpServer));
    }

    @PostMapping
    public Result<McpServerResponse> save(@Valid @RequestBody McpServerRequest request) {
        if (mcpServerService.findByName(request.name()) != null) {
            return Result.error(400, "McpServer name already exists");
        }

        McpServer mcpServer = mcpServerConverter.toEntity(request);
        mcpServer.setStatus(Status.ACTIVE.getCode());

        boolean success = mcpServerService.save(mcpServer);
        if (success) {
            return Result.success(mcpServerConverter.toResponse(mcpServer));
        } else {
            return Result.error("Failed to save");
        }
    }

    @PutMapping("/{id}")
    public Result<McpServerResponse> update(
            @PathVariable Integer id, @Valid @RequestBody McpServerRequest request) {
        McpServer existing = mcpServerService.findById(id);
        if (existing == null) {
            return Result.error(404, "McpServer not found");
        }

        McpServer mcpServer = mcpServerConverter.toEntity(request);
        mcpServer.setId(id);
        mcpServer.setStatus(existing.getStatus());

        boolean success = mcpServerService.update(mcpServer);
        if (success) {
            return Result.success(mcpServerConverter.toResponse(mcpServer));
        } else {
            return Result.error("Failed to update");
        }
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteById(@PathVariable Integer id) {
        boolean success = mcpServerService.deleteById(id);
        return success ? Result.success(true) : Result.error("Failed to delete");
    }

    @PutMapping("/{id}/enable")
    public Result<Boolean> enable(@PathVariable Integer id) {
        return updateStatus(id, Status.ACTIVE, "Failed to enable");
    }

    @PutMapping("/{id}/disable")
    public Result<Boolean> disable(@PathVariable Integer id) {
        return updateStatus(id, Status.INACTIVE, "Failed to disable");
    }

    @GetMapping("/status/{status}")
    public Result<List<McpServerResponse>> findByStatus(@PathVariable String status) {
        return Result.success(toResponses(mcpServerService.findByStatus(status)));
    }

    private List<McpServerResponse> toResponses(List<McpServer> servers) {
        return servers.stream().map(mcpServerConverter::toResponse).toList();
    }

    private Result<Boolean> updateStatus(Integer id, Status status, String failureMessage) {
        McpServer mcpServer = mcpServerService.findById(id);
        if (mcpServer == null) {
            return Result.error(404, "McpServer not found");
        }
        mcpServer.setStatus(status.getCode());
        boolean success = mcpServerService.update(mcpServer);
        return success ? Result.success(true) : Result.error(failureMessage);
    }
}

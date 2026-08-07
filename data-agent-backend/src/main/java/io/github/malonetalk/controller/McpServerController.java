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

import io.github.malonetalk.annotation.AdminOnly;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.Result;
import io.github.malonetalk.convertor.McpServerConverter;
import io.github.malonetalk.dto.McpServerRequest;
import io.github.malonetalk.dto.McpServerResponse;
import io.github.malonetalk.entity.McpServer;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.exception.BusinessException;
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

@AdminOnly
@RestController
@AllArgsConstructor
@RequestMapping("/api/mcp-server")
public class McpServerController {

    private final McpServerService mcpServerService;
    private final McpServerConverter mcpServerConverter;

    @GetMapping
    public Result<List<McpServerResponse>> findAll() {
        List<McpServer> list = mcpServerService.findAll();
        List<McpServerResponse> responses =
                list.stream().map(mcpServerConverter::toResponse).toList();
        return Result.success(responses);
    }

    @GetMapping("/{id}")
    public Result<McpServerResponse> findById(@PathVariable Integer id) {
        return Result.success(mcpServerConverter.toResponse(requireMcpServer(id)));
    }

    @PostMapping
    public Result<McpServerResponse> save(@Valid @RequestBody McpServerRequest request) {
        if (mcpServerService.findByName(request.name()) != null) {
            throw BusinessException.of(ErrorCode.DATA_CONFLICT, "MCP server name already exists.");
        }

        McpServer mcpServer = mcpServerConverter.toEntity(request);
        mcpServer.setStatus(Status.ACTIVE.getCode());

        requireOperationSuccess(mcpServerService.save(mcpServer), "Failed to save the MCP server.");
        return Result.success(mcpServerConverter.toResponse(mcpServer));
    }

    @PutMapping("/{id}")
    public Result<McpServerResponse> update(
            @PathVariable Integer id, @Valid @RequestBody McpServerRequest request) {
        McpServer existing = requireMcpServer(id);
        McpServer nameConflict = mcpServerService.findByName(request.name());
        if (nameConflict != null && !id.equals(nameConflict.getId())) {
            throw BusinessException.of(ErrorCode.DATA_CONFLICT, "MCP server name already exists.");
        }

        McpServer mcpServer = mcpServerConverter.toEntity(request);
        mcpServer.setId(id);
        mcpServer.setStatus(existing.getStatus());

        requireOperationSuccess(
                mcpServerService.update(mcpServer), "Failed to update the MCP server.");
        return Result.success(mcpServerConverter.toResponse(mcpServer));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteById(@PathVariable Integer id) {
        requireMcpServer(id);
        requireOperationSuccess(
                mcpServerService.deleteById(id), "Failed to delete the MCP server.");
        return Result.success(true);
    }

    @PutMapping("/{id}/enable")
    public Result<Boolean> enable(@PathVariable Integer id) {
        McpServer mcpServer = requireMcpServer(id);
        mcpServer.setStatus(Status.ACTIVE.getCode());
        requireOperationSuccess(
                mcpServerService.update(mcpServer), "Failed to enable the MCP server.");
        return Result.success(true);
    }

    @PutMapping("/{id}/disable")
    public Result<Boolean> disable(@PathVariable Integer id) {
        McpServer mcpServer = requireMcpServer(id);
        mcpServer.setStatus(Status.INACTIVE.getCode());
        requireOperationSuccess(
                mcpServerService.update(mcpServer), "Failed to disable the MCP server.");
        return Result.success(true);
    }

    @GetMapping("/status/{status}")
    public Result<List<McpServerResponse>> findByStatus(@PathVariable String status) {
        List<McpServer> list = mcpServerService.findByStatus(status);
        List<McpServerResponse> responses =
                list.stream().map(mcpServerConverter::toResponse).toList();
        return Result.success(responses);
    }

    private McpServer requireMcpServer(Integer id) {
        McpServer mcpServer = mcpServerService.findById(id);
        if (mcpServer == null) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "MCP server not found.");
        }
        return mcpServer;
    }

    private void requireOperationSuccess(boolean success, String message) {
        if (!success) {
            throw BusinessException.of(ErrorCode.OPERATION_FAILED, message);
        }
    }
}

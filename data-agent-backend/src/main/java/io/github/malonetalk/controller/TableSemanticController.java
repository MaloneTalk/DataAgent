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
import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.BatchResetTableSemanticRequest;
import io.github.malonetalk.dto.semantic.TableSemanticResponse;
import io.github.malonetalk.dto.semantic.TableSemanticUpdateRequest;
import io.github.malonetalk.service.semantic.table.TableSemanticService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/semantic/tables")
@RequiredArgsConstructor
public class TableSemanticController {

    private final TableSemanticService tableSemanticService;

    @GetMapping
    public Result<PageResponse<TableSemanticResponse>> findAllTables(
            @RequestParam @NotNull @Min(1) Integer datasourceId,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(defaultValue = "asc")
                    @Pattern(
                            regexp = "^(?i)(asc|desc)$",
                            message = "sortOrder must be asc or desc.")
                    String sortOrder) {
        return Result.success(
                tableSemanticService.getTablePage(
                        datasourceId, PageRequest.of(page, pageSize), keyword, sortOrder));
    }

    @GetMapping("/domains")
    public Result<List<String>> listTableDomains(
            @RequestParam @NotNull @Min(1) Integer datasourceId) {
        return Result.success(tableSemanticService.listAvailableDomains(datasourceId));
    }

    @PutMapping
    public Result<Boolean> updateTableSemantic(
            @Valid @RequestBody TableSemanticUpdateRequest request) {
        tableSemanticService.updateTableSemantic(request);
        return Result.success(true);
    }

    @DeleteMapping
    public Result<Boolean> resetTableSemantic(
            @RequestParam @NotNull @Min(1) Integer datasourceId,
            @RequestParam @NotBlank String tableName) {
        tableSemanticService.resetTableSemantic(datasourceId, tableName);
        return Result.success(true);
    }

    @DeleteMapping("/batch")
    public Result<Integer> resetTableSemantics(
            @Valid @RequestBody BatchResetTableSemanticRequest request) {
        return Result.success(
                tableSemanticService.resetTableSemantics(
                        request.datasourceId(), request.tableNames()));
    }
}

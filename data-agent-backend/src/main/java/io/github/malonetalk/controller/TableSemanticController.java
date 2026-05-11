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
import io.github.malonetalk.service.SemanticSchemaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tableinfo/semantic")
public class TableSemanticController {

    private final SemanticSchemaService semanticSchemaService;

    public TableSemanticController(SemanticSchemaService semanticSchemaService) {
        this.semanticSchemaService = semanticSchemaService;
    }

    @GetMapping("/tables")
    public Result<PageResponse<TableSemanticResponse>> findAllTables(
            @RequestParam(required = false) Integer datasourceId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return Result.success(
                    semanticSchemaService.getTablePage(
                            datasourceId, PageRequest.of(page, pageSize)));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/tables")
    public Result<Boolean> updateTableSemantic(
            @Valid @RequestBody TableSemanticUpdateRequest request) {
        boolean success = semanticSchemaService.updateTableSemantic(request);
        return success ? Result.success(true) : Result.error("Failed to update table semantic");
    }

    @DeleteMapping("/tables")
    public Result<Boolean> resetTableSemantic(
            @RequestParam Integer datasourceId, @RequestParam String tableName) {
        boolean success = semanticSchemaService.resetTableSemantic(datasourceId, tableName);
        return success ? Result.success(true) : Result.error("Failed to reset table semantic");
    }

    @DeleteMapping("/tables/batch")
    public Result<Integer> resetTableSemantics(
            @Valid @RequestBody BatchResetTableSemanticRequest request) {
        try {
            return Result.success(
                    semanticSchemaService.resetTableSemantics(
                            request.datasourceId(), request.tableNames()));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}

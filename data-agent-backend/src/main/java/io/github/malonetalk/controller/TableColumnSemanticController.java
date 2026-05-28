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
import io.github.malonetalk.dto.semantic.BatchResetColumnSemanticRequest;
import io.github.malonetalk.dto.semantic.ColumnSemanticResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticUpdateRequest;
import io.github.malonetalk.service.semantic.column.ColumnSemanticService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/semantic/tables/{tableName}/columns")
public class TableColumnSemanticController {

    private final ColumnSemanticService columnSemanticService;

    public TableColumnSemanticController(ColumnSemanticService columnSemanticService) {
        this.columnSemanticService = columnSemanticService;
    }

    @GetMapping
    public Result<PageResponse<ColumnSemanticResponse>> findAllColumns(
            @PathVariable String tableName,
            @RequestParam Integer datasourceId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(name = "keywordPrefix", required = false) String keywordPrefix,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        return Result.success(
                columnSemanticService.getColumnPage(
                        datasourceId,
                        tableName,
                        PageRequest.of(page, pageSize),
                        keywordPrefix,
                        sortOrder));
    }

    @PutMapping
    public Result<Boolean> updateColumnSemantic(
            @PathVariable String tableName,
            @RequestParam Integer datasourceId,
            @Valid @RequestBody ColumnSemanticUpdateRequest request) {
        columnSemanticService.updateColumnSemantic(datasourceId, tableName, request);
        return Result.success(true);
    }

    @DeleteMapping
    public Result<Boolean> resetColumnSemantic(
            @PathVariable String tableName,
            @RequestParam Integer datasourceId,
            @RequestParam String columnName) {
        columnSemanticService.resetColumnSemantic(datasourceId, tableName, columnName);
        return Result.success(true);
    }

    @DeleteMapping("/batch")
    public Result<Integer> resetColumnSemantics(
            @PathVariable String tableName,
            @Valid @RequestBody BatchResetColumnSemanticRequest request) {
        return Result.success(
                columnSemanticService.resetColumnSemantics(
                        request.datasourceId(), tableName, request.columnNames()));
    }
}

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
import io.github.malonetalk.dto.semantic.ColumnSemanticResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticUpdateRequest;
import io.github.malonetalk.service.SemanticSchemaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tableinfo/{tableName}/semantic/columns")
public class TableColumnSemanticController {

    private final SemanticSchemaService semanticSchemaService;

    public TableColumnSemanticController(SemanticSchemaService semanticSchemaService) {
        this.semanticSchemaService = semanticSchemaService;
    }

    @GetMapping
    public Result<List<ColumnSemanticResponse>> findAllColumns(
            @PathVariable String tableName, @RequestParam Integer datasourceId) {
        List<ColumnSemanticResponse> list =
                semanticSchemaService.getAllColumns(datasourceId, tableName);
        return Result.success(list);
    }

    @PutMapping
    public Result<Boolean> updateColumnSemantic(
            @PathVariable String tableName,
            @RequestParam Integer datasourceId,
            @Valid @RequestBody ColumnSemanticUpdateRequest request) {
        boolean success =
                semanticSchemaService.updateColumnSemantic(datasourceId, tableName, request);
        return success ? Result.success(true) : Result.error("Failed to update column semantic");
    }

    @DeleteMapping
    public Result<Boolean> resetColumnSemantic(
            @PathVariable String tableName,
            @RequestParam Integer datasourceId,
            @RequestParam String columnName) {
        boolean success =
                semanticSchemaService.resetColumnSemantic(datasourceId, tableName, columnName);
        return success ? Result.success(true) : Result.error("Failed to reset column semantic");
    }
}

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
import io.github.malonetalk.dto.semantic.BatchDeleteLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.BindLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateColumnResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateTableResponse;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationEnabledRequest;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationRequest;
import io.github.malonetalk.service.semantic.relation.RelationSemanticService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tableinfo/semantic/relations")
public class TableRelationSemanticController {

    private final RelationSemanticService relationSemanticService;

    public TableRelationSemanticController(RelationSemanticService relationSemanticService) {
        this.relationSemanticService = relationSemanticService;
    }

    @GetMapping("/candidate/tables")
    public Result<PageResponse<RelationCandidateTableResponse>> listCandidateTables(
            @RequestParam Integer datasourceId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(name = "keywordPrefix", required = false) String keywordPrefix,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        return Result.success(
                relationSemanticService.getRelationCandidateTablePage(
                        datasourceId, PageRequest.of(page, pageSize), keywordPrefix, sortOrder));
    }

    @GetMapping("/candidate/{tableName}/columns")
    public Result<PageResponse<RelationCandidateColumnResponse>> listCandidateColumns(
            @PathVariable String tableName,
            @RequestParam Integer datasourceId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(name = "keywordPrefix", required = false) String keywordPrefix,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        return Result.success(
                relationSemanticService.getRelationCandidateColumnPage(
                        datasourceId,
                        tableName,
                        PageRequest.of(page, pageSize),
                        keywordPrefix,
                        sortOrder));
    }

    @GetMapping("/{tableName}")
    public Result<PageResponse<LogicalTableRelationResponse>> listByTable(
            @PathVariable String tableName,
            @RequestParam Integer datasourceId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(name = "keywordPrefix", required = false) String keywordPrefix,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.success(
                relationSemanticService.getRelationPage(
                        datasourceId,
                        tableName,
                        PageRequest.of(page, pageSize),
                        keywordPrefix,
                        enabled,
                        sortOrder));
    }

    @PostMapping("/{tableName}")
    public Result<LogicalTableRelationResponse> create(
            @PathVariable String tableName,
            @RequestParam Integer datasourceId,
            @Valid @RequestBody BindLogicalTableRelationRequest request) {
        return Result.success(
                relationSemanticService.createRelationSemantic(datasourceId, tableName, request));
    }

    @PutMapping("/{tableName}/{relationId}")
    public Result<LogicalTableRelationResponse> update(
            @PathVariable String tableName,
            @PathVariable Integer relationId,
            @RequestParam Integer datasourceId,
            @Valid @RequestBody UpdateLogicalTableRelationRequest request) {
        return Result.success(
                relationSemanticService.updateRelationSemantic(
                        datasourceId, tableName, relationId, request));
    }

    @PutMapping("/{tableName}/{relationId}/enabled")
    public Result<Boolean> updateEnabled(
            @PathVariable String tableName,
            @PathVariable Integer relationId,
            @RequestParam Integer datasourceId,
            @Valid @RequestBody UpdateLogicalTableRelationEnabledRequest request) {
        return Result.success(
                relationSemanticService.updateRelationSemanticEnabled(
                        datasourceId, tableName, relationId, request.enabled()));
    }

    @DeleteMapping("/{tableName}/{relationId}")
    public Result<Boolean> delete(
            @PathVariable String tableName,
            @PathVariable Integer relationId,
            @RequestParam Integer datasourceId) {
        return Result.success(
                relationSemanticService.deleteRelationSemantic(
                        datasourceId, tableName, relationId));
    }

    @DeleteMapping("/{tableName}/batch")
    public Result<Integer> deleteBatch(
            @PathVariable String tableName,
            @Valid @RequestBody BatchDeleteLogicalTableRelationRequest request) {
        return Result.success(
                relationSemanticService.deleteRelationSemantics(
                        request.datasourceId(), tableName, request.relationIds()));
    }
}

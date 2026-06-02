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
import io.github.malonetalk.dto.PageResponse;
import io.github.malonetalk.dto.semantic.BatchDeleteLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.BindLogicalTableRelationRequest;
import io.github.malonetalk.dto.semantic.LogicalTableRelationResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateColumnResponse;
import io.github.malonetalk.dto.semantic.RelationCandidateTableResponse;
import io.github.malonetalk.dto.semantic.RelationSemanticPageQuery;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationEnabledRequest;
import io.github.malonetalk.dto.semantic.UpdateLogicalTableRelationRequest;
import io.github.malonetalk.service.semantic.relation.RelationSemanticService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/semantic/tables")
@RequiredArgsConstructor
public class RelationSemanticController {

    private final RelationSemanticService relationSemanticService;

    @GetMapping("/{tableName}/relations")
    public Result<PageResponse<LogicalTableRelationResponse>> listByTable(
            @PathVariable @NotBlank String tableName, @Valid RelationSemanticPageQuery query) {
        return Result.success(
                relationSemanticService.getRelationPage(
                        new RelationSemanticPageQuery(
                                query.datasourceId(),
                                tableName,
                                query.page(),
                                query.pageSize(),
                                query.keyword(),
                                query.enabled(),
                                query.sortOrder())));
    }

    @PostMapping("/{tableName}/relations")
    public Result<LogicalTableRelationResponse> create(
            @PathVariable @NotBlank String tableName,
            @Valid @RequestBody BindLogicalTableRelationRequest request) {
        return Result.success(relationSemanticService.createRelationSemantic(tableName, request));
    }

    @PutMapping("/{tableName}/relations")
    public Result<LogicalTableRelationResponse> update(
            @PathVariable @NotBlank String tableName,
            @Valid @RequestBody UpdateLogicalTableRelationRequest request) {
        return Result.success(relationSemanticService.updateRelationSemantic(tableName, request));
    }

    @PutMapping("/{tableName}/relations/enabled")
    public Result<Boolean> updateEnabled(
            @PathVariable @NotBlank String tableName,
            @Valid @RequestBody UpdateLogicalTableRelationEnabledRequest request) {
        return Result.success(
                relationSemanticService.updateRelationSemanticEnabled(tableName, request));
    }

    @DeleteMapping("/{tableName}/relations/{relationId}")
    public Result<Boolean> delete(
            @PathVariable @NotBlank String tableName,
            @PathVariable @NotNull @Min(1) Integer relationId,
            @RequestParam @NotNull @Min(1) Integer datasourceId) {
        return Result.success(
                relationSemanticService.deleteRelationSemantic(
                        datasourceId, tableName, relationId));
    }

    @DeleteMapping("/{tableName}/relations/batch")
    public Result<Integer> deleteBatch(
            @PathVariable @NotBlank String tableName,
            @Valid @RequestBody BatchDeleteLogicalTableRelationRequest request) {
        return Result.success(
                relationSemanticService.deleteRelationSemantics(
                        request.datasourceId(), tableName, request.relationIds()));
    }

    @GetMapping("/relations/candidate/tables")
    public Result<PageResponse<RelationCandidateTableResponse>> candidateTables(
            @Valid RelationSemanticPageQuery query) {
        return Result.success(relationSemanticService.getCandidateTablePage(query));
    }

    @GetMapping("/{tableName}/relations/candidate/columns")
    public Result<PageResponse<RelationCandidateColumnResponse>> candidateColumns(
            @PathVariable @NotBlank String tableName, @Valid RelationSemanticPageQuery query) {
        return Result.success(
                relationSemanticService.getCandidateColumnPage(
                        new RelationSemanticPageQuery(
                                query.datasourceId(),
                                tableName,
                                query.page(),
                                query.pageSize(),
                                query.keyword(),
                                query.enabled(),
                                query.sortOrder())));
    }
}

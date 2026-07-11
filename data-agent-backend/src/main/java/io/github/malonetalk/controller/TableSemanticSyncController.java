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
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.PhysicalTableCandidatePageQuery;
import io.github.malonetalk.dto.semantic.PhysicalTableCandidateResponse;
import io.github.malonetalk.dto.semantic.RefreshPhysicalStatusRequest;
import io.github.malonetalk.dto.semantic.SyncTableSemanticsRequest;
import io.github.malonetalk.dto.semantic.SyncTableSemanticsResponse;
import io.github.malonetalk.service.semantic.sync.SemanticSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/semantic/tables/sync")
@RequiredArgsConstructor
public class TableSemanticSyncController {

    private final SemanticSyncService semanticSyncService;

    @GetMapping("/candidates")
    public Result<PageResponse<PhysicalTableCandidateResponse>> getPhysicalTableCandidates(
            @Valid PhysicalTableCandidatePageQuery query) {
        return Result.success(semanticSyncService.getPhysicalTableCandidates(query));
    }

    @PostMapping
    public Result<SyncTableSemanticsResponse> syncTables(
            @Valid @RequestBody SyncTableSemanticsRequest request) {
        return Result.success(semanticSyncService.syncTables(request));
    }

    @PostMapping("/physical-status")
    public Result<SyncTableSemanticsResponse> refreshPhysicalStatus(
            @Valid @RequestBody RefreshPhysicalStatusRequest request) {
        return Result.success(semanticSyncService.refreshPhysicalStatus(request));
    }
}

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
import io.github.malonetalk.common.Result;
import io.github.malonetalk.dto.semantic.RelationWorkspacePageQuery;
import io.github.malonetalk.dto.semantic.RelationWorkspaceResponse;
import io.github.malonetalk.service.semantic.relation.RelationSemanticService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AdminOnly
@RestController
@RequestMapping("/api/semantic/tables/relations/workspace")
@RequiredArgsConstructor
public class TableRelationWorkspaceController {

    private final RelationSemanticService relationSemanticService;

    @GetMapping
    public Result<RelationWorkspaceResponse> getWorkspace(@Valid RelationWorkspacePageQuery query) {
        return Result.success(relationSemanticService.getRelationWorkspace(query));
    }
}

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
import io.github.malonetalk.dto.DomainCreateRequest;
import io.github.malonetalk.dto.DomainPageQuery;
import io.github.malonetalk.dto.DomainUpdateRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.DomainInfo;
import io.github.malonetalk.service.DomainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;

    @GetMapping
    public Result<PageResponse<DomainInfo>> findDomains(@Valid DomainPageQuery query) {
        return Result.success(domainService.getDomainPage(query));
    }

    @GetMapping("/{id}")
    public Result<DomainInfo> findById(@PathVariable Integer id) {
        DomainInfo domain = domainService.findById(id);
        if (domain == null) {
            return Result.error(404, "领域不存在");
        }
        return Result.success(domain);
    }

    @PostMapping
    public Result<DomainInfo> create(@Valid @RequestBody DomainCreateRequest request) {
        return Result.success(domainService.create(request));
    }

    @PutMapping("/{id}")
    public Result<DomainInfo> update(
            @PathVariable Integer id, @Valid @RequestBody DomainUpdateRequest request) {
        return Result.success(domainService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Integer id) {
        domainService.delete(id);
        return Result.success(true);
    }
}

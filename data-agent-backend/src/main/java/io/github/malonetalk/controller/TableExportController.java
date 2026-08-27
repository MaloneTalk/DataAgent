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
import io.github.malonetalk.common.UserContext;
import io.github.malonetalk.dto.TableExportPageQuery;
import io.github.malonetalk.dto.TableExportResponse;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.service.TableExportService;
import io.github.malonetalk.service.TableExportService.TableExportDownload;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/table-exports")
public class TableExportController {

    private final TableExportService tableExportService;

    @GetMapping
    public Result<PageResponse<TableExportResponse>> findExports(
            @Valid TableExportPageQuery query) {
        return Result.success(
                tableExportService.getExportPage(query, UserContext.requireScopedUserId()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        TableExportDownload file =
                tableExportService.findDownload(id, UserContext.requireScopedUserId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName())
                                .build()
                                .toString())
                .body(file.content());
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable String id) {
        tableExportService.deleteById(id, UserContext.requireScopedUserId());
        return Result.success(true);
    }
}

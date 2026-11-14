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

import io.github.malonetalk.annotation.RequirePermission;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.model.dto.CsvImportConfirmDto;
import io.github.malonetalk.model.dto.CsvParseOptions;
import io.github.malonetalk.model.vo.BooleanVo;
import io.github.malonetalk.model.vo.CsvImportPageVo;
import io.github.malonetalk.model.vo.CsvImportVo;
import io.github.malonetalk.service.csv.CsvImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/csv-imports")
@RequiredArgsConstructor
public class CsvImportController {
    private final CsvImportService service;

    @PostMapping("/preview")
    @RequirePermission(PermissionEnum.DATA_CSV_IMPORT)
    public CsvImportVo preview(
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "UTF-8") String encoding,
            @RequestParam(defaultValue = ",") String delimiter,
            @RequestParam(defaultValue = "true") boolean header,
            @RequestParam(defaultValue = "true") boolean emptyAsNull)
            throws Exception {
        return service.preview(
                file,
                CsvParseOptions.builder()
                        .encoding(encoding)
                        .delimiter(delimiter)
                        .header(header)
                        .emptyAsNull(emptyAsNull)
                        .build());
    }

    @PutMapping("/{id}/preview")
    @RequirePermission(PermissionEnum.DATA_CSV_IMPORT)
    public CsvImportVo repreview(
            @PathVariable String id, @Valid @RequestBody CsvParseOptions options) throws Exception {
        return service.repreview(id, options);
    }

    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequirePermission({PermissionEnum.DATA_CSV_IMPORT, PermissionEnum.SEMANTIC_TABLE_SYNC})
    public CsvImportVo confirm(
            @PathVariable String id, @Valid @RequestBody CsvImportConfirmDto request) {
        return service.confirm(id, request);
    }

    @GetMapping("/{id}")
    @RequirePermission(
            value = {
                PermissionEnum.DATA_CSV_IMPORT,
                PermissionEnum.DATA_CSV_DELETE,
                PermissionEnum.DATA_DATASOURCE_READ
            },
            anyOf = true)
    public CsvImportVo get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping
    @RequirePermission(
            value = {
                PermissionEnum.DATA_CSV_IMPORT,
                PermissionEnum.DATA_CSV_DELETE,
                PermissionEnum.DATA_DATASOURCE_READ
            },
            anyOf = true)
    public CsvImportPageVo list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return service.list(page, pageSize);
    }

    @PostMapping("/{id}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequirePermission({PermissionEnum.DATA_CSV_IMPORT, PermissionEnum.SEMANTIC_TABLE_SYNC})
    public BooleanVo sync(@PathVariable String id) {
        service.retrySync(id);
        return BooleanVo.TRUE;
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionEnum.DATA_CSV_DELETE)
    public BooleanVo delete(@PathVariable String id) throws Exception {
        service.delete(id);
        return BooleanVo.TRUE;
    }
}

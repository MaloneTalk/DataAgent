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
package io.github.malonetalk.model.converter;

import io.github.malonetalk.enums.CsvImportStatus;
import io.github.malonetalk.model.bo.CsvPreviewBo;
import io.github.malonetalk.model.dto.CsvParseOptions;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.model.vo.CsvImportVo;
import java.time.LocalDateTime;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        uses = CsvSchemaConverter.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CsvImportConverter {
    @Mapping(target = "columns", source = "task.columnsJson")
    @Mapping(target = "options", source = "task.parseOptionsJson")
    CsvImportVo toVo(CsvImportPo task, List<List<String>> preview);

    default CsvImportPo previewTask(
            String id,
            Integer datasourceId,
            String filename,
            String hash,
            long size,
            CsvParseOptions options,
            CsvPreviewBo preview,
            CsvSchemaConverter schema) {
        CsvImportPo task = new CsvImportPo();
        task.setId(id);
        task.setDatasourceId(datasourceId);
        task.setOriginalFilename(filename);
        task.setDisplayName(filename.substring(0, Math.min(filename.length() - 4, 200)));
        task.setDescription("");
        task.setPhysicalTableName("csv_t_" + id);
        task.setAttemptId("");
        task.setFileSha256(hash);
        task.setFileSize(size);
        task.setParseOptionsJson(schema.json(options));
        task.setColumnsJson(schema.json(preview.columns()));
        task.setStatus(CsvImportStatus.PREVIEWED);
        task.setProcessedRows(0L);
        task.setRowCount(0L);
        task.setExpiresAt(LocalDateTime.now().plusHours(24));
        return task;
    }
}

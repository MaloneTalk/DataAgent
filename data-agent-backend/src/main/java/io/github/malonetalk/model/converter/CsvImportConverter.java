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

import io.github.malonetalk.common.CsvLimits;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.model.bo.CsvPreviewBo;
import io.github.malonetalk.model.dto.CsvColumnDto;
import io.github.malonetalk.model.dto.CsvParseOptions;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.model.vo.CsvImportVo;
import io.github.malonetalk.utils.CsvPhysicalNames;
import io.github.malonetalk.utils.JsonUtil;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.type.TypeReference;

@Mapper(
        componentModel = "spring",
        imports = {CsvPhysicalNames.class, CsvLimits.class},
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class CsvImportConverter {
    @Autowired private JsonUtil jsonUtil;

    @Mapping(target = "columns", source = "task.columnsJson")
    @Mapping(target = "options", source = "task.parseOptionsJson")
    public abstract CsvImportVo toVo(CsvImportPo task, List<List<String>> preview);

    // Map upload metadata only; the service owns the initial status and retention policy.
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "datasourceId", source = "datasourceId")
    @Mapping(target = "originalFilename", source = "filename")
    @Mapping(
            target = "displayName",
            expression =
                    "java(filename.substring(0, Math.min(filename.length() - 4,"
                            + " CsvLimits.MAX_NAME_LENGTH)))")
    @Mapping(target = "description", constant = "")
    @Mapping(target = "physicalTableName", expression = "java(CsvPhysicalNames.table(id))")
    @Mapping(target = "attemptId", constant = "")
    @Mapping(target = "fileSha256", source = "hash")
    @Mapping(target = "fileSize", source = "size")
    @Mapping(target = "parseOptionsJson", source = "options", qualifiedByName = "csvJson")
    @Mapping(target = "columnsJson", source = "preview.columns", qualifiedByName = "csvJson")
    @Mapping(target = "processedRows", constant = "0L")
    @Mapping(target = "rowCount", constant = "0L")
    public abstract CsvImportPo previewTask(
            String id,
            Integer datasourceId,
            String filename,
            String hash,
            long size,
            CsvParseOptions options,
            CsvPreviewBo preview);

    // Corrupt persisted metadata remains an internal failure, not a user-facing business error.
    @Named("csvJson")
    public String json(Object value) {
        try {
            return jsonUtil.toJson(value);
        } catch (BusinessException e) {
            throw new IllegalStateException("Cannot serialize CSV metadata", e.getCause());
        }
    }

    public List<CsvColumnDto> columns(String json) {
        try {
            return jsonUtil.toObj(json, new TypeReference<>() {});
        } catch (BusinessException e) {
            throw new IllegalStateException("Cannot read CSV columns", e.getCause());
        }
    }

    public CsvParseOptions options(String json) {
        try {
            return jsonUtil.toObj(json, new TypeReference<>() {});
        } catch (BusinessException e) {
            throw new IllegalStateException("Cannot read CSV options", e.getCause());
        }
    }
}

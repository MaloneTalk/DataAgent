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
package io.github.malonetalk.service.csv;

import static io.github.malonetalk.enums.CsvImportStatus.DATA_READY;
import static io.github.malonetalk.enums.CsvImportStatus.DELETE_FAILED;
import static io.github.malonetalk.enums.CsvImportStatus.DELETING;
import static io.github.malonetalk.enums.CsvImportStatus.EXPIRED;
import static io.github.malonetalk.enums.CsvImportStatus.IMPORTING;
import static io.github.malonetalk.enums.CsvImportStatus.IMPORT_FAILED;
import static io.github.malonetalk.enums.CsvImportStatus.PREVIEWED;
import static io.github.malonetalk.enums.CsvImportStatus.QUEUED;
import static io.github.malonetalk.enums.CsvImportStatus.READY;
import static io.github.malonetalk.enums.CsvImportStatus.SYNCING;
import static io.github.malonetalk.enums.CsvImportStatus.SYNC_FAILED;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.malonetalk.config.CsvImportConfig;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.CsvImportMapper;
import io.github.malonetalk.model.converter.CsvImportConverter;
import io.github.malonetalk.model.converter.CsvSchemaConverter;
import io.github.malonetalk.model.dto.CsvImportConfirmDto;
import io.github.malonetalk.model.dto.CsvParseOptions;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.model.vo.CsvImportPageVo;
import io.github.malonetalk.model.vo.CsvImportVo;
import io.github.malonetalk.service.DataPermissionService;
import io.github.malonetalk.utils.CsvPhysicalNames;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CsvImportService {
    private final CsvImportConfig config;
    private final CsvImportMapper imports;
    private final CsvDatasourceService datasources;
    private final CsvFileService files;
    private final CsvSchemaConverter schema;
    private final CsvImportConverter converter;
    private final UserContextHolder holder;
    private final DataPermissionService dataPermissions;
    private final CsvImportDeleteService deletion;
    private final CsvTableImportService tables;

    public CsvImportVo preview(MultipartFile file, CsvParseOptions options) throws Exception {
        DatasourcePo datasource = datasources.requireActive();
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv") || filename.length() > 255)
            throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "请选择文件名不超过 255 字的 CSV 文件。");
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            String hash = files.save(id, file);
            var preview = files.preview(id, options);
            CsvImportPo task =
                    converter.previewTask(
                            id,
                            datasource.getId(),
                            filename,
                            hash,
                            file.getSize(),
                            options,
                            preview);
            task.setStatus(PREVIEWED);
            task.setExpiresAt(LocalDateTime.now().plusHours(24));
            imports.insert(task);
            return converter.toVo(task, preview.rows());
        } catch (Exception e) {
            files.delete(id);
            throw e;
        }
    }

    public CsvImportVo repreview(String id, CsvParseOptions options) throws Exception {
        CsvImportPo task = accessible(id);
        requirePreviewable(task);
        var preview = files.preview(id, options);
        int updated =
                imports.update(
                        Wrappers.<CsvImportPo>lambdaUpdate()
                                .eq(CsvImportPo::getId, id)
                                .in(CsvImportPo::getStatus, PREVIEWED, IMPORT_FAILED)
                                .set(CsvImportPo::getParseOptionsJson, schema.json(options))
                                .set(CsvImportPo::getColumnsJson, schema.json(preview.columns())));
        if (updated != 1)
            throw BusinessException.of(ErrorCode.CSV_TASK_STATE_CONFLICT, "任务状态已变化，请刷新后重试。");
        return converter.toVo(accessible(id), preview.rows());
    }

    public CsvImportVo confirm(String id, CsvImportConfirmDto request) {
        datasources.requireActive();
        CsvImportPo task = accessible(id);
        var submitted = Set.of(QUEUED, IMPORTING, DATA_READY, SYNCING, READY, SYNC_FAILED);
        if (submitted.contains(task.getStatus())) return converter.toVo(task, List.of());
        requirePreviewable(task);
        schema.validate(request.columns(), schema.columns(task.getColumnsJson()).size());
        String attemptId = UUID.randomUUID().toString().replace("-", "");
        int updated =
                imports.update(
                        Wrappers.<CsvImportPo>lambdaUpdate()
                                .eq(CsvImportPo::getId, id)
                                .in(CsvImportPo::getStatus, PREVIEWED, IMPORT_FAILED)
                                .eq(CsvImportPo::getAttemptId, task.getAttemptId())
                                .set(CsvImportPo::getDisplayName, request.displayName())
                                .set(
                                        CsvImportPo::getDescription,
                                        Optional.ofNullable(request.description()).orElse(""))
                                .set(CsvImportPo::getColumnsJson, schema.json(request.columns()))
                                .set(CsvImportPo::getStatus, QUEUED)
                                // A new table isolates a resumed worker from the retry's data.
                                .set(
                                        CsvImportPo::getPhysicalTableName,
                                        CsvPhysicalNames.table(attemptId))
                                .set(CsvImportPo::getAttemptId, attemptId)
                                .set(CsvImportPo::getSubmittedBy, holder.checkAndGet().userId())
                                .set(CsvImportPo::getProcessedRows, 0)
                                .set(CsvImportPo::getRowCount, 0)
                                .set(CsvImportPo::getErrorMessage, ""));
        if (updated != 1) {
            // Concurrent confirmations reuse the winning submission without creating another batch.
            CsvImportPo current = accessible(id);
            if (submitted.contains(current.getStatus())) return converter.toVo(current, List.of());
            throw BusinessException.of(ErrorCode.CSV_TASK_STATE_CONFLICT, "任务状态已变化，请刷新后重试。");
        }
        return get(id);
    }

    public CsvImportVo get(String id) {
        return converter.toVo(accessible(id), List.of());
    }

    public CsvImportPageVo list(int page, int pageSize) {
        int size = Math.min(100, Math.max(1, pageSize));
        int number = Math.max(1, page);
        var user = holder.checkAndGet();
        var query = Wrappers.<CsvImportPo>lambdaQuery();
        if (!Boolean.TRUE.equals(user.superAdmin())) {
            var scope = dataPermissions.loadGrantedScope(user, config.getDatasourceId());
            query.and(
                    nested -> {
                        nested.eq(CsvImportPo::getCreatorId, user.userId());
                        if (!scope.tables().isEmpty())
                            nested.or(
                                    granted ->
                                            granted.in(
                                                    CsvImportPo::getPhysicalTableName,
                                                    scope.tables().values()));
                    });
        }
        var result =
                imports.selectPage(
                        new Page<>(number, size), query.orderByDesc(CsvImportPo::getCreateTime));
        return new CsvImportPageVo(
                result.getRecords().stream().map(row -> converter.toVo(row, List.of())).toList(),
                result.getTotal(),
                number,
                size);
    }

    public void retrySync(String id) {
        CsvImportPo task = accessible(id);
        if (!Set.of(DATA_READY, SYNC_FAILED).contains(task.getStatus()))
            throw BusinessException.of(ErrorCode.CSV_TASK_STATE_CONFLICT, "该任务无需重试同步。");
        datasources.requireActive();
        int updated =
                imports.update(
                        Wrappers.<CsvImportPo>lambdaUpdate()
                                .eq(CsvImportPo::getId, id)
                                .eq(CsvImportPo::getStatus, task.getStatus())
                                .eq(CsvImportPo::getAttemptId, task.getAttemptId())
                                .set(CsvImportPo::getStatus, DATA_READY)
                                // Preserve the physical data and original creator when taking over
                                // a retry.
                                .set(
                                        CsvImportPo::getAttemptId,
                                        UUID.randomUUID().toString().replace("-", ""))
                                .set(CsvImportPo::getSubmittedBy, holder.checkAndGet().userId())
                                .set(CsvImportPo::getErrorMessage, ""));
        if (updated != 1)
            throw BusinessException.of(ErrorCode.CSV_TASK_STATE_CONFLICT, "任务状态已变化，请刷新后重试。");
    }

    public void delete(String id) throws Exception {
        accessible(id);
        CsvImportPo task = deletion.begin(id);
        try {
            if (!Set.of(PREVIEWED, IMPORT_FAILED, EXPIRED).contains(task.getStatus()))
                tables.drop(task, datasources.requireActive());
            files.delete(id);
            deletion.finish(task);
        } catch (Exception e) {
            imports.update(
                    Wrappers.<CsvImportPo>lambdaUpdate()
                            .eq(CsvImportPo::getId, id)
                            .eq(CsvImportPo::getStatus, DELETING)
                            .set(CsvImportPo::getStatus, DELETE_FAILED)
                            .set(CsvImportPo::getErrorMessage, "删除未完成，请检查导入库连接后重试。"));
            throw BusinessException.of(ErrorCode.CSV_DELETE_FAILED, "删除未完成，请检查导入库连接后重试。", e);
        }
    }

    private CsvImportPo accessible(String id) {
        CsvImportPo task =
                Optional.ofNullable(imports.selectById(id))
                        .orElseThrow(
                                () ->
                                        BusinessException.of(
                                                ErrorCode.CSV_TASK_NOT_FOUND, "导入记录不存在。"));
        var user = holder.checkAndGet();
        if (!Boolean.TRUE.equals(user.superAdmin())
                && !Long.valueOf(user.userId()).equals(task.getCreatorId())) {
            if (!dataPermissions
                    .loadGrantedScope(user, task.getDatasourceId())
                    .canTable(task.getPhysicalTableName()))
                throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        return task;
    }

    private void requirePreviewable(CsvImportPo task) {
        if (!Set.of(PREVIEWED, IMPORT_FAILED).contains(task.getStatus())
                || task.getExpiresAt().isBefore(LocalDateTime.now())
                || !Files.isRegularFile(files.path(task.getId())))
            throw BusinessException.of(ErrorCode.CSV_TASK_STATE_CONFLICT, "文件已过期或任务已经提交，请重新上传。");
    }
}

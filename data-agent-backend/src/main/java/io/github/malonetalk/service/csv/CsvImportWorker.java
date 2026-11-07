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

import static io.github.malonetalk.enums.CsvImportStatus.CLEANING;
import static io.github.malonetalk.enums.CsvImportStatus.CLEANUP_FAILED;
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
import static io.github.malonetalk.enums.PermissionEnum.DATA_CSV_IMPORT;
import static io.github.malonetalk.enums.PermissionEnum.SEMANTIC_TABLE_SYNC;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.config.CsvImportConfig;
import io.github.malonetalk.enums.CsvImportStatus;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.CsvImportMapper;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.service.PermissionService;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportWorker {
    private final CsvImportConfig config;
    private final CsvImportMapper imports;
    private final CsvImportService service;
    private final CsvTableImportService tables;
    private final CsvImportPublishService publisher;
    private final CsvFileService files;
    private final PermissionService permissions;
    private final UserContextHolder holder;
    private final ThreadPoolTaskExecutor csvImportExecutor;

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void dispatch() {
        if (config.getDatasourceId() <= 0) return;
        var tasks =
                imports.selectList(
                        Wrappers.<CsvImportPo>lambdaQuery()
                                .eq(CsvImportPo::getDatasourceId, config.getDatasourceId())
                                .in(CsvImportPo::getStatus, QUEUED, DATA_READY)
                                .orderByAsc(CsvImportPo::getCreateTime)
                                .last("LIMIT 2"));
        for (CsvImportPo task : tasks) {
            if (csvImportExecutor.getActiveCount() >= 2) break;
            try {
                csvImportExecutor.execute(() -> run(task));
            } catch (TaskRejectedException ignored) {
                break; // The durable queue is retried on the next poll.
            }
        }
    }

    private void run(CsvImportPo task) {
        boolean importing = task.getStatus() == QUEUED;
        if (importing && !transition(task, QUEUED, IMPORTING)) return;
        try {
            var user =
                    permissions.require(
                            Math.toIntExact(task.getCreatorId()),
                            DATA_CSV_IMPORT,
                            SEMANTIC_TABLE_SYNC);
            holder.set(user);
            var datasource = service.datasource();
            if (importing) {
                long count =
                        tables.importTable(
                                task,
                                datasource,
                                rows -> {
                                    int changed =
                                            imports.update(
                                                    Wrappers.<CsvImportPo>lambdaUpdate()
                                                            .eq(CsvImportPo::getId, task.getId())
                                                            .eq(CsvImportPo::getStatus, IMPORTING)
                                                            .set(
                                                                    CsvImportPo::getProcessedRows,
                                                                    rows));
                                    if (changed != 1)
                                        throw new IllegalStateException("Import was interrupted");
                                });
                task.setRowCount(count);
                imports.update(
                        Wrappers.<CsvImportPo>lambdaUpdate()
                                .eq(CsvImportPo::getId, task.getId())
                                .eq(CsvImportPo::getStatus, IMPORTING)
                                .set(CsvImportPo::getRowCount, count));
                if (!transition(task, IMPORTING, DATA_READY)) return;
            }
            if (!transition(task, DATA_READY, SYNCING)) return;
            publisher.publish(task, publisher.snapshot(task, datasource));
            removeFile(task);
        } catch (Exception e) {
            fail(task, task.getStatus() == IMPORTING ? IMPORT_FAILED : SYNC_FAILED, e);
        } finally {
            holder.clear();
        }
    }

    private boolean transition(CsvImportPo task, CsvImportStatus from, CsvImportStatus to) {
        int changed =
                imports.update(
                        Wrappers.<CsvImportPo>lambdaUpdate()
                                .eq(CsvImportPo::getId, task.getId())
                                .eq(CsvImportPo::getStatus, from)
                                .set(CsvImportPo::getStatus, to));
        if (changed == 1) task.setStatus(to);
        return changed == 1;
    }

    private void fail(CsvImportPo task, CsvImportStatus status, Exception error) {
        String message =
                error instanceof BusinessException
                        ? error.getMessage()
                        : status == IMPORT_FAILED
                                ? "CSV 解析或数据库写入失败，请检查文件格式、编码、字段类型和导入库连接。"
                                : "语义同步失败，请检查分析账号的读取权限后重试同步。";
        CsvImportStatus target = status == IMPORT_FAILED ? CLEANING : SYNC_FAILED;
        int changed =
                imports.update(
                        Wrappers.<CsvImportPo>lambdaUpdate()
                                .eq(CsvImportPo::getId, task.getId())
                                .eq(CsvImportPo::getStatus, task.getStatus())
                                .set(CsvImportPo::getStatus, target)
                                .set(
                                        CsvImportPo::getErrorMessage,
                                        message.substring(0, Math.min(500, message.length()))));
        log.warn(
                "CSV task {} failed in {} ({})",
                task.getId(),
                task.getStatus(),
                error.getClass().getSimpleName());
        if (changed == 1 && target == CLEANING) {
            task.setStatus(CLEANING);
            cleanupTable(task);
        }
    }

    private void cleanupTable(CsvImportPo task) {
        try {
            tables.drop(task, service.datasource());
            transition(task, CLEANING, IMPORT_FAILED);
        } catch (Exception e) {
            transition(task, CLEANING, CLEANUP_FAILED);
            log.warn("CSV table cleanup deferred: {}", task.getId());
        }
    }

    private void removeFile(CsvImportPo task) {
        try {
            files.delete(task.getId());
            imports.update(
                    Wrappers.<CsvImportPo>lambdaUpdate()
                            .eq(CsvImportPo::getId, task.getId())
                            .set(CsvImportPo::getExpiresAt, null));
        } catch (Exception e) {
            log.warn("CSV file cleanup deferred: {}", task.getId());
        }
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanup() {
        if (config.getDatasourceId() <= 0) return;
        LocalDateTime now = LocalDateTime.now();
        // A batch has a 30 second database timeout. Ten minutes without progress means the worker
        // was lost.
        imports.update(
                Wrappers.<CsvImportPo>lambdaUpdate()
                        .eq(CsvImportPo::getDatasourceId, config.getDatasourceId())
                        .in(CsvImportPo::getStatus, IMPORTING, CLEANING)
                        .lt(CsvImportPo::getUpdateTime, now.minusMinutes(10))
                        .set(CsvImportPo::getStatus, CLEANUP_FAILED)
                        .set(CsvImportPo::getErrorMessage, "导入任务中断，请重新确认导入。"));
        imports.update(
                Wrappers.<CsvImportPo>lambdaUpdate()
                        .eq(CsvImportPo::getDatasourceId, config.getDatasourceId())
                        .eq(CsvImportPo::getStatus, SYNCING)
                        .lt(CsvImportPo::getUpdateTime, now.minusMinutes(10))
                        .set(CsvImportPo::getStatus, SYNC_FAILED)
                        .set(CsvImportPo::getErrorMessage, "语义同步中断，请重试同步。"));
        var query =
                Wrappers.<CsvImportPo>lambdaQuery()
                        .eq(CsvImportPo::getDatasourceId, config.getDatasourceId());
        query.and(
                filter ->
                        filter.in(CsvImportPo::getStatus, CLEANUP_FAILED, DELETING)
                                .or()
                                .in(
                                        CsvImportPo::getStatus,
                                        PREVIEWED,
                                        IMPORT_FAILED,
                                        READY,
                                        EXPIRED)
                                .isNotNull(CsvImportPo::getExpiresAt)
                                .lt(CsvImportPo::getExpiresAt, now));
        List<CsvImportPo> tasks =
                imports.selectList(query.orderByAsc(CsvImportPo::getUpdateTime).last("LIMIT 100"));
        for (CsvImportPo task : tasks) {
            try {
                if (task.getStatus() == CLEANUP_FAILED
                        && transition(task, CLEANUP_FAILED, CLEANING)) cleanupTable(task);
                if (task.getStatus() == DELETING
                        && task.getUpdateTime().isBefore(now.minusMinutes(10)))
                    transition(task, DELETING, DELETE_FAILED);
                if (task.getStatus() == READY || task.getStatus() == EXPIRED) removeFile(task);
                else if ((task.getStatus() == PREVIEWED || task.getStatus() == IMPORT_FAILED)
                        && task.getExpiresAt().isBefore(now)) {
                    if (transition(task, task.getStatus(), EXPIRED)) removeFile(task);
                }
            } catch (Exception e) {
                log.warn("CSV cleanup deferred: {}", task.getId());
            }
        }
        cleanupOrphanFiles();
    }

    private void cleanupOrphanFiles() {
        if (!Files.isDirectory(config.getDirectory())) return;
        try (var paths = Files.list(config.getDirectory())) {
            var candidates =
                    paths.filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .matches("[a-f0-9]{32}[.]csv"))
                            .iterator();
            while (candidates.hasNext()) {
                var path = candidates.next();
                String id = path.getFileName().toString().substring(0, 32);
                if (Files.getLastModifiedTime(path)
                                .toInstant()
                                .isBefore(Instant.now().minus(1, ChronoUnit.DAYS))
                        && imports.selectById(id) == null) files.delete(id);
            }
        } catch (Exception e) {
            log.warn("CSV orphan file cleanup deferred");
        }
    }
}

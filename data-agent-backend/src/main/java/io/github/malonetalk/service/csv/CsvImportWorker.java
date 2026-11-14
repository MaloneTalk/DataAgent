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
import static io.github.malonetalk.enums.CsvImportStatus.DATA_READY;
import static io.github.malonetalk.enums.CsvImportStatus.IMPORTING;
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
import io.github.malonetalk.exception.ExceptionResponseMapper;
import io.github.malonetalk.mapper.CsvImportMapper;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.PermissionService;
import jakarta.annotation.Resource;
import java.util.Optional;
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
    private final CsvDatasourceService datasources;
    private final CsvTableImportService tables;
    private final CsvImportPublishService publisher;
    private final CsvImportCleanupService cleanup;
    private final PermissionService permissions;
    private final UserContextHolder holder;
    private final ExceptionResponseMapper exceptionResponses;

    @Resource(name = "csvImportExecutor")
    private ThreadPoolTaskExecutor executor;

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void dispatch() {
        if (config.getDatasourceId() <= 0) return;
        var tasks =
                imports.selectList(
                        Wrappers.<CsvImportPo>lambdaQuery()
                                .eq(CsvImportPo::getDatasourceId, config.getDatasourceId())
                                .in(CsvImportPo::getStatus, QUEUED, DATA_READY)
                                .orderByAsc(CsvImportPo::getCreateTime)
                                .last("LIMIT " + executor.getMaxPoolSize()));
        for (CsvImportPo task : tasks) {
            if (executor.getActiveCount() >= executor.getMaxPoolSize()) break;
            try {
                executor.execute(() -> run(task));
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
                            Optional.ofNullable(task.getSubmittedBy())
                                    .orElseGet(() -> Math.toIntExact(task.getCreatorId())),
                            DATA_CSV_IMPORT,
                            SEMANTIC_TABLE_SYNC);
            holder.set(user);
            var datasource = datasources.requireActive();
            if (importing && !importData(task, datasource)) return;
            publish(task, datasource);
        } catch (Exception e) {
            fail(task, e);
        } finally {
            holder.clear();
        }
    }

    private boolean importData(CsvImportPo task, DatasourcePo datasource) throws Exception {
        long count = tables.importTable(task, datasource, rows -> updateProgress(task, rows));
        // Complete the data phase atomically under the same attempt and status guards.
        int changed =
                imports.update(
                        Wrappers.<CsvImportPo>lambdaUpdate()
                                .eq(CsvImportPo::getId, task.getId())
                                .eq(CsvImportPo::getAttemptId, task.getAttemptId())
                                .eq(CsvImportPo::getStatus, IMPORTING)
                                .set(CsvImportPo::getRowCount, count)
                                .set(CsvImportPo::getStatus, DATA_READY));
        if (changed != 1) {
            cleanup.discardStaleTable(task);
            return false;
        }
        task.setRowCount(count);
        task.setStatus(DATA_READY);
        return true;
    }

    private void updateProgress(CsvImportPo task, long rows) {
        int changed =
                imports.update(
                        Wrappers.<CsvImportPo>lambdaUpdate()
                                .eq(CsvImportPo::getId, task.getId())
                                .eq(CsvImportPo::getAttemptId, task.getAttemptId())
                                .eq(CsvImportPo::getStatus, IMPORTING)
                                .set(CsvImportPo::getProcessedRows, rows));
        if (changed != 1) throw new IllegalStateException("Import was interrupted");
    }

    private void publish(CsvImportPo task, DatasourcePo datasource) {
        if (!transition(task, DATA_READY, SYNCING)) return;
        publisher.publish(task, publisher.snapshot(task, datasource));
        task.setStatus(READY);
        cleanup.removeFile(task);
    }

    private boolean transition(CsvImportPo task, CsvImportStatus from, CsvImportStatus to) {
        int changed =
                imports.update(
                        Wrappers.<CsvImportPo>lambdaUpdate()
                                .eq(CsvImportPo::getId, task.getId())
                                .eq(CsvImportPo::getAttemptId, task.getAttemptId())
                                .eq(CsvImportPo::getStatus, from)
                                .set(CsvImportPo::getStatus, to));
        if (changed == 1) task.setStatus(to);
        return changed == 1;
    }

    private void fail(CsvImportPo task, Exception error) {
        // Log the original cause before a status update or compensation can fail as well.
        logTaskFailure(task, "execution", error);
        boolean importing = task.getStatus() == IMPORTING;
        String message;
        if (error instanceof BusinessException) message = error.getMessage();
        else if (importing) message = "CSV 解析或数据库写入失败，请检查文件格式、编码、字段类型和导入库连接。";
        else message = "语义同步失败，请检查分析账号的读取权限后重试同步。";
        CsvImportStatus target = importing ? CLEANING : SYNC_FAILED;
        int changed =
                imports.update(
                        Wrappers.<CsvImportPo>lambdaUpdate()
                                .eq(CsvImportPo::getId, task.getId())
                                .eq(CsvImportPo::getAttemptId, task.getAttemptId())
                                .eq(CsvImportPo::getStatus, task.getStatus())
                                .set(CsvImportPo::getStatus, target)
                                .set(
                                        CsvImportPo::getErrorMessage,
                                        message.substring(0, Math.min(500, message.length()))));
        if (changed == 1 && target == CLEANING) {
            task.setStatus(CLEANING);
            cleanup.cleanupTable(task);
        } else if (changed == 0 && task.getStatus() == IMPORTING) {
            cleanup.discardStaleTable(task);
        }
    }

    private void logTaskFailure(CsvImportPo task, String phase, Exception error) {
        String context =
                "CSV %s [taskId=%s, attemptId=%s, status=%s, table=%s]"
                        .formatted(
                                phase,
                                task.getId(),
                                task.getAttemptId(),
                                task.getStatus(),
                                task.getPhysicalTableName());
        exceptionResponses.logMapped(log, error, exceptionResponses.resolve(error), context);
    }
}

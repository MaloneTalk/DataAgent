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
package io.github.malonetalk.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.github.malonetalk.agent.SessionService;
import io.github.malonetalk.agent.datasource.DynamicDataSourceManager;
import io.github.malonetalk.agent.datasource.SqlExecutor;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.dto.TableExportPageQuery;
import io.github.malonetalk.dto.TableExportResponse;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.TableExport;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.TableExportMapper;
import io.github.malonetalk.utils.RequestAssert;
import io.github.malonetalk.utils.SemanticUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableExportServiceImpl implements TableExportService {

    private static final int FETCH_SIZE = 1000;
    private static final int QUERY_TIMEOUT_SECONDS = 120;
    private static final int MAX_TITLE_LENGTH = 255;

    private final TableExportMapper tableExportMapper;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final SqlExecutor sqlExecutor;
    private final SessionService sessionService;

    @Value("${data-agent.export-dir:${user.dir}/exports}")
    private String exportDir;

    @Override
    public TableExportResponse create(
            String sessionId, Datasource datasource, String title, String sql) {
        String exportSessionId =
                RequestAssert.requireNotBlank(sessionId, "sessionId cannot be blank.");
        RequestAssert.requireNonNull(datasource, "datasource cannot be null.");
        String exportTitle = title == null || title.isBlank() ? "table-export" : title.trim();
        if (exportTitle.length() > MAX_TITLE_LENGTH) {
            exportTitle = exportTitle.substring(0, MAX_TITLE_LENGTH);
        }
        String safeSql = sqlExecutor.validateSelectSql(sql);

        String exportId = UUID.randomUUID().toString();
        Path target = exportFile(exportId);
        ensureInExportDir(target);

        try {
            int rowCount = writeCsv(datasource, safeSql, target);
            TableExport tableExport = new TableExport();
            tableExport.setId(exportId);
            tableExport.setSessionId(exportSessionId);
            tableExport.setTitle(exportTitle);
            tableExport.setRowCount(rowCount);
            if (tableExportMapper.insert(tableExport) <= 0) {
                throw BusinessException.of(
                        ErrorCode.OPERATION_FAILED, "Failed to save table export.");
            }
            return toResponse(tableExport);
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    @Override
    public PageResponse<TableExportResponse> getExportPage(
            TableExportPageQuery query, Integer userId) {
        String sessionId = SemanticUtils.trimToNull(query.sessionId());
        if (sessionId != null) {
            sessionService.requireOwnership(userId, sessionId);
        }
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        Page<Object> startedPage = PageHelper.startPage(pageNumber, pageSize);
        try {
            Page<TableExport> page =
                    (Page<TableExport>) tableExportMapper.selectPage(sessionId, userId);
            List<TableExportResponse> responses =
                    page.getResult().stream().map(this::toResponse).toList();
            return PageResponse.of(responses, page.getTotal(), pageNumber, pageSize);
        } finally {
            startedPage.close();
        }
    }

    @Override
    public Path findDownload(String id, Integer userId) {
        TableExport tableExport = requireExport(id);
        sessionService.requireOwnership(userId, tableExport.getSessionId());
        Path file = exportFile(id);
        ensureInExportDir(file);
        if (!Files.exists(file)) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND, "Export file does not exist: id=" + id);
        }
        return file;
    }

    @Override
    public void deleteById(String id, Integer userId) {
        TableExport tableExport = requireExport(id);
        sessionService.requireOwnership(userId, tableExport.getSessionId());
        Path file = exportFile(id);
        ensureInExportDir(file);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw BusinessException.of(
                    ErrorCode.OPERATION_FAILED, "Failed to delete export file.", e);
        }
        if (tableExportMapper.deleteById(id) == 0) {
            throw exportNotFound(id);
        }
    }

    private int writeCsv(Datasource datasource, String sql, Path target) {
        DataSource dataSource = dynamicDataSourceManager.getOrCreateDataSource(datasource);
        try {
            Files.createDirectories(target.getParent());
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement stmt =
                            conn.prepareStatement(
                                    sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                    BufferedWriter writer =
                            Files.newBufferedWriter(
                                    target,
                                    StandardCharsets.UTF_8,
                                    StandardOpenOption.CREATE_NEW)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setFetchSize(
                        "mysql".equalsIgnoreCase(datasource.getType())
                                ? Integer.MIN_VALUE
                                : FETCH_SIZE);
                writer.write('\ufeff');
                try (ResultSet rs = stmt.executeQuery()) {
                    return writeRows(writer, rs);
                }
            }
        } catch (SQLException e) {
            throw BusinessException.of(ErrorCode.SQL_EXECUTION_FAILED, "SQL export failed.", e);
        } catch (IOException e) {
            throw BusinessException.of(
                    ErrorCode.OPERATION_FAILED, "Failed to write export file.", e);
        }
    }

    private int writeRows(BufferedWriter writer, ResultSet rs) throws SQLException, IOException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                writer.write(',');
            }
            writer.write(csvCell(metaData.getColumnLabel(i)));
        }
        writer.newLine();

        int rowCount = 0;
        while (rs.next()) {
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) {
                    writer.write(',');
                }
                Object value = rs.getObject(i);
                writer.write(csvCell(value == null ? "" : String.valueOf(value)));
            }
            writer.newLine();
            rowCount++;
        }
        return rowCount;
    }

    private String csvCell(String value) {
        String safeValue = preventFormulaInjection(value);
        if (safeValue.contains(",")
                || safeValue.contains("\"")
                || safeValue.contains("\n")
                || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    private String preventFormulaInjection(String value) {
        if (value.isEmpty()) {
            return value;
        }
        String checkValue = value.stripLeading();
        if (checkValue.isEmpty()) {
            return value;
        }
        char first = checkValue.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + value;
        }
        return value;
    }

    private Path rootDir() {
        return Path.of(exportDir).toAbsolutePath().normalize();
    }

    private Path exportFile(String id) {
        return rootDir().resolve(id + ".csv").normalize();
    }

    private void ensureInExportDir(Path target) {
        if (!target.startsWith(rootDir())) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "Invalid export file path.");
        }
    }

    private TableExport requireExport(String id) {
        RequestAssert.requireNotBlank(id, "id cannot be blank.");
        TableExport tableExport = tableExportMapper.selectById(id);
        if (tableExport == null) {
            throw exportNotFound(id);
        }
        return tableExport;
    }

    private TableExportResponse toResponse(TableExport tableExport) {
        return new TableExportResponse(
                tableExport.getId(),
                tableExport.getTitle(),
                tableExport.getRowCount(),
                tableExport.getSessionId(),
                tableExport.getCreateTime());
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete export file: {}", file, e);
        }
    }

    private BusinessException exportNotFound(String id) {
        return BusinessException.of(
                ErrorCode.RESOURCE_NOT_FOUND, "Table export does not exist: id=" + id);
    }
}

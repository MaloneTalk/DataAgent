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

import static io.github.malonetalk.model.converter.CsvSchemaConverter.invalid;

import io.github.malonetalk.config.CsvImportConfig;
import io.github.malonetalk.model.converter.CsvSchemaConverter;
import io.github.malonetalk.model.dto.CsvColumnDto;
import io.github.malonetalk.model.po.CsvImportPo;
import io.github.malonetalk.model.po.DatasourcePo;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Properties;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CsvTableImportService {
    private final CsvImportConfig config;
    private final CsvFileService files;
    private final CsvSchemaConverter converter;

    public long importTable(CsvImportPo task, DatasourcePo datasource, LongConsumer progress)
            throws Exception {
        if (!datasource.getId().equals(task.getDatasourceId())) throw invalid("任务不属于当前 CSV 数据源。");
        files.verify(task.getId(), task.getFileSha256());
        List<CsvColumnDto> columns = converter.columns(task.getColumnsJson());
        converter.validate(columns, columns.size());
        try (Connection connection = connect(datasource)) {
            createTable(connection, task, columns);
            connection.setAutoCommit(false);
            try {
                long count = writeRows(connection, task, columns, progress);
                connection.commit();
                return count;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private Connection connect(DatasourcePo datasource) throws Exception {
        if (!config.getWriterUrl().startsWith("jdbc:mysql://")
                || config.getWriterUsername().isBlank()) throw invalid("请先配置 CSV 导入库的写入连接和账号。");
        Properties properties = new Properties();
        properties.setProperty("user", config.getWriterUsername());
        properties.setProperty("password", config.getWriterPassword());
        properties.setProperty("connectTimeout", "10000");
        properties.setProperty("socketTimeout", "30000");
        properties.setProperty("rewriteBatchedStatements", "true");
        properties.setProperty("characterEncoding", "UTF-8");
        Connection connection = DriverManager.getConnection(config.getWriterUrl(), properties);
        try {
            if (!datasource.getDatabaseName().equals(connection.getCatalog()))
                throw invalid("CSV 写入连接与分析数据源必须使用同一个数据库。");
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(30);
                statement.execute(
                        "SET SESSION sql_mode ="
                            + " 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'");
            }
            return connection;
        } catch (Exception e) {
            connection.close();
            throw e;
        }
    }

    private void createTable(Connection connection, CsvImportPo task, List<CsvColumnDto> columns)
            throws Exception {
        String definitions =
                columns.stream()
                        .map(
                                column ->
                                        "`"
                                                + converter.physicalColumn(column.index())
                                                + "` "
                                                + converter.sqlType(column)
                                                + " NULL COMMENT '"
                                                + comment(column.name())
                                                + "'")
                        .collect(Collectors.joining(","));
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            statement.execute(
                    "CREATE TABLE "
                            + table(task)
                            + " ("
                            + definitions
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='"
                            + comment(task.getDisplayName())
                            + "'");
        }
    }

    private long writeRows(
            Connection connection,
            CsvImportPo task,
            List<CsvColumnDto> columns,
            LongConsumer progress)
            throws Exception {
        var options = converter.options(task.getParseOptionsJson());
        String placeholders = columns.stream().map(column -> "?").collect(Collectors.joining(","));
        try (CSVParser parser = files.open(task.getId(), options);
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO " + table(task) + " VALUES (" + placeholders + ")")) {
            statement.setQueryTimeout(30);
            long count = 0;
            int batchRows = 0;
            long batchBytes = 0;
            for (CSVRecord record : parser) {
                files.validate(record, columns.size());
                if (record.getRecordNumber() == 1 && options.header()) continue;
                if (++count > 100000) throw invalid("CSV 最多允许 100000 条数据记录。");
                task.setProcessedRows(count);
                long bytes =
                        record.toList().stream()
                                .mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length)
                                .sum();
                if (batchRows > 0 && batchBytes + bytes > 1024 * 1024) {
                    flush(connection, statement, count - 1, progress);
                    batchRows = 0;
                    batchBytes = 0;
                }
                bind(statement, record, columns, options.emptyAsNull());
                statement.addBatch();
                batchRows++;
                batchBytes += bytes;
                if (batchRows == 1000) {
                    flush(connection, statement, count, progress);
                    batchRows = 0;
                    batchBytes = 0;
                }
            }
            if (count == 0) throw invalid("CSV 没有数据记录。");
            if (batchRows > 0) flush(connection, statement, count, progress);
            return count;
        }
    }

    private void bind(
            PreparedStatement statement,
            CSVRecord record,
            List<CsvColumnDto> columns,
            boolean emptyAsNull)
            throws Exception {
        for (CsvColumnDto column : columns) {
            try {
                statement.setObject(
                        column.index() + 1,
                        converter
                                .value(record.get(column.index()), column, emptyAsNull)
                                .orElse(null));
            } catch (RuntimeException e) {
                // Do not include cell contents in errors: uploads may contain sensitive values.
                throw invalid(
                        "第 "
                                + record.getRecordNumber()
                                + " 条记录，第 "
                                + (column.index() + 1)
                                + " 列不能转换为 "
                                + converter.sqlType(column)
                                + "，请调整字段类型后重新导入。");
            }
        }
    }

    private void flush(
            Connection connection, PreparedStatement statement, long count, LongConsumer progress)
            throws Exception {
        statement.executeBatch();
        statement.clearBatch();
        connection.commit();
        progress.accept(count);
    }

    public void drop(CsvImportPo task, DatasourcePo datasource) throws Exception {
        if (!datasource.getId().equals(task.getDatasourceId())) throw invalid("任务不属于当前 CSV 数据源。");
        try (Connection connection = connect(datasource);
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            statement.execute("DROP TABLE IF EXISTS " + table(task));
        }
    }

    private String table(CsvImportPo task) {
        if (!task.getPhysicalTableName().equals("csv_t_" + task.getId())
                || !task.getId().matches("[a-f0-9]{32}")) throw invalid("CSV 物理表名称无效。");
        return "`" + task.getPhysicalTableName() + "`";
    }

    private String comment(String text) {
        return text.replace("\\", "\\\\").replace("'", "''").replace("\0", "");
    }
}

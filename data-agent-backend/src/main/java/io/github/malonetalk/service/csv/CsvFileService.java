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

import io.github.malonetalk.config.CsvImportConfig;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.bo.CsvPreviewBo;
import io.github.malonetalk.model.converter.CsvSchemaConverter;
import io.github.malonetalk.model.dto.CsvColumnDto;
import io.github.malonetalk.model.dto.CsvParseOptions;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CsvFileService {
    public static final long MAX_BYTES = 50L * 1024 * 1024;
    private final CsvImportConfig config;
    private final CsvSchemaConverter converter;

    public String save(String id, MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > MAX_BYTES)
            throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "文件必须在 1 字节到 50 MiB 之间。");
        Files.createDirectories(config.getDirectory());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(file.getInputStream(), digest);
                var output = Files.newOutputStream(path(id))) {
            byte[] buffer = new byte[8192];
            long count = 0;
            for (int length; (length = input.read(buffer)) != -1; ) {
                count += length;
                if (count > MAX_BYTES)
                    throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "文件超过 50 MiB。");
                output.write(buffer, 0, length);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public void verify(String id, String expectedHash) throws Exception {
        if (Files.size(path(id)) > MAX_BYTES)
            throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "文件超过 50 MiB。");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(path(id)), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        if (!expectedHash.equals(HexFormat.of().formatHex(digest.digest())))
            throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "上传文件发生变化，请重新上传。");
    }

    public Path path(String id) {
        if (!id.matches("[a-f0-9]{32}"))
            throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "无效的导入 ID。");
        return config.getDirectory().toAbsolutePath().normalize().resolve(id + ".csv");
    }

    public void delete(String id) throws IOException {
        Files.deleteIfExists(path(id));
    }

    public CSVParser open(String id, CsvParseOptions options) throws IOException {
        if (options.encoding() == null
                || options.delimiter() == null
                || !Set.of("UTF-8", "GB18030").contains(options.encoding())
                || !Set.of(",", ";", "\t").contains(options.delimiter()))
            throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "不支持的编码或分隔符。");
        Reader reader =
                new InputStreamReader(
                        Files.newInputStream(path(id)),
                        Charset.forName(options.encoding())
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT));
        PushbackReader input = new PushbackReader(reader, 1);
        try {
            int first = input.read();
            if (first != -1 && first != 0xFEFF) input.unread(first);
            return CSVFormat.RFC4180
                    .builder()
                    .setDelimiter(options.delimiter().charAt(0))
                    .setIgnoreEmptyLines(true)
                    .get()
                    .parse(input);
        } catch (Exception e) {
            input.close();
            throw e;
        }
    }

    public CsvPreviewBo preview(String id, CsvParseOptions options) throws IOException {
        try (CSVParser parser = open(id, options)) {
            var iterator = parser.iterator();
            if (!iterator.hasNext())
                throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "CSV 没有数据。");
            CSVRecord first = iterator.next();
            validate(first, first.size());
            List<String> names = new ArrayList<>();
            List<List<String>> sample = new ArrayList<>();
            for (int i = 0; i < first.size(); i++) {
                String name =
                        options.header() && !first.get(i).isBlank() ? first.get(i) : "字段" + (i + 1);
                if (name.length() > 200)
                    throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "表头不能超过 200 字。");
                names.add(name);
            }
            if (!options.header()) sample.add(first.toList());
            while (sample.size() < 1000 && iterator.hasNext()) {
                CSVRecord record = iterator.next();
                validate(record, names.size());
                sample.add(record.toList());
            }
            if (sample.isEmpty())
                throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "CSV 只有表头，没有数据记录。");
            List<CsvColumnDto> columns = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                final int index = i;
                columns.add(
                        converter.infer(
                                i,
                                names.get(i),
                                sample.stream().map(row -> row.get(index)).toList(),
                                options.emptyAsNull()));
            }
            return new CsvPreviewBo(columns, sample.stream().limit(20).toList());
        } catch (IOException | UncheckedIOException e) {
            throw BusinessException.of(ErrorCode.CSV_FILE_INVALID, "无法解析 CSV，请检查文件编码、分隔符和引号格式。");
        }
    }

    public void validate(CSVRecord record, int columns) {
        if (columns < 1 || columns > 100 || record.size() != columns)
            throw BusinessException.of(
                    ErrorCode.CSV_FILE_INVALID,
                    "第 " + record.getRecordNumber() + " 条记录列数不一致，最多允许 100 列。");
        for (int i = 0; i < record.size(); i++) {
            if (record.get(i).getBytes(StandardCharsets.UTF_8).length > 16 * 1024)
                throw BusinessException.of(
                        ErrorCode.CSV_FILE_INVALID,
                        "第 " + record.getRecordNumber() + " 条记录，第 " + (i + 1) + " 列超过 16 KiB。");
        }
    }
}

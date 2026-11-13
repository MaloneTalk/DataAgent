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

import static io.github.malonetalk.common.CsvLimits.MAX_COLUMNS;
import static io.github.malonetalk.common.CsvLimits.MAX_NAME_LENGTH;

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.dto.CsvColumnDto;
import io.github.malonetalk.utils.DateTimeUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CsvSchemaConverter {
    public void validate(List<CsvColumnDto> columns, int expected) {
        if (columns.size() != expected || expected < 1 || expected > MAX_COLUMNS) {
            throw BusinessException.of(
                    ErrorCode.CSV_SCHEMA_INVALID, "字段数量与文件不一致，最多允许 " + MAX_COLUMNS + " 列。");
        }
        int rowBytes = 0;
        for (int i = 0; i < columns.size(); i++) {
            CsvColumnDto column = columns.get(i);
            if (column.index() != i
                    || column.name() == null
                    || column.name().isBlank()
                    || column.name().length() > MAX_NAME_LENGTH) {
                throw BusinessException.of(
                        ErrorCode.CSV_SCHEMA_INVALID,
                        "字段必须按原文件顺序提交，并填写不超过 " + MAX_NAME_LENGTH + " 字的名称。");
            }
            sqlType(column);
            rowBytes += "VARCHAR".equals(column.type()) ? column.length() * 4 + 2 : 32;
        }
        if (rowBytes > 60000)
            throw BusinessException.of(
                    ErrorCode.CSV_SCHEMA_INVALID, "VARCHAR 字段总长度过大，请将部分字段改为 TEXT。");
    }

    public String sqlType(CsvColumnDto column) {
        return switch (column.type()) {
            case "BIGINT", "DATE", "DATETIME", "TEXT" -> column.type();
            case "BOOLEAN" -> "TINYINT(1)";
            case "VARCHAR" -> {
                if (column.length() == null || column.length() < 1 || column.length() > 4000)
                    throw BusinessException.of(
                            ErrorCode.CSV_SCHEMA_INVALID, "VARCHAR 长度必须在 1 到 4000 之间。");
                yield "VARCHAR(" + column.length() + ")";
            }
            case "DECIMAL" -> {
                if (column.precision() == null
                        || column.scale() == null
                        || column.precision() < 1
                        || column.precision() > 65
                        || column.scale() < 0
                        || column.scale() > 30
                        || column.scale() > column.precision())
                    throw BusinessException.of(ErrorCode.CSV_SCHEMA_INVALID, "DECIMAL 精度或小数位数无效。");
                yield "DECIMAL(" + column.precision() + "," + column.scale() + ")";
            }
            default -> throw BusinessException.of(ErrorCode.CSV_SCHEMA_INVALID, "不支持的字段类型。");
        };
    }

    public Optional<Object> value(String raw, CsvColumnDto column, boolean emptyAsNull) {
        if (raw.isEmpty() && emptyAsNull) return Optional.empty();
        String text = raw.strip();
        Object value =
                switch (column.type()) {
                    case "VARCHAR" -> {
                        if (raw.codePointCount(0, raw.length()) > column.length())
                            throw BusinessException.of(
                                    ErrorCode.CSV_SCHEMA_INVALID,
                                    "文本超过 VARCHAR 长度，请改为 TEXT 或增加长度。");
                        yield raw;
                    }
                    case "TEXT" -> raw;
                    case "BIGINT" -> {
                        if (!text.matches("[+-]?[0-9]+"))
                            throw BusinessException.of(ErrorCode.CSV_SCHEMA_INVALID, "不是有效整数。");
                        yield Long.valueOf(text);
                    }
                    case "DECIMAL" -> {
                        BigDecimal number =
                                new BigDecimal(text)
                                        .setScale(column.scale(), RoundingMode.UNNECESSARY);
                        if (number.precision() > column.precision())
                            throw BusinessException.of(
                                    ErrorCode.CSV_SCHEMA_INVALID, "数值超过 DECIMAL 精度。");
                        yield number;
                    }
                    case "BOOLEAN" -> {
                        if (!Set.of("true", "false").contains(text.toLowerCase(Locale.ROOT)))
                            throw BusinessException.of(
                                    ErrorCode.CSV_SCHEMA_INVALID, "布尔值必须为 true 或 false。");
                        yield Boolean.parseBoolean(text);
                    }
                    case "DATE" -> DateTimeUtils.parseMysqlDate(text);
                    case "DATETIME" -> DateTimeUtils.parseMysqlDateTime(text);
                    default ->
                            throw BusinessException.of(ErrorCode.CSV_SCHEMA_INVALID, "不支持的字段类型。");
                };
        return Optional.of(value);
    }

    public CsvColumnDto infer(int index, String name, List<String> values, boolean emptyAsNull) {
        List<String> present =
                values.stream()
                        .filter(s -> !emptyAsNull || !s.isEmpty())
                        .map(String::strip)
                        .toList();
        String type = "VARCHAR";
        int scale = 0;
        if (!present.isEmpty()) {
            Set<String> kinds = present.stream().map(this::kind).collect(Collectors.toSet());
            if (kinds.size() == 1) type = kinds.iterator().next();
            else if (Set.of("BIGINT", "DECIMAL").containsAll(kinds)) type = "DECIMAL";
            if ("DECIMAL".equals(type)) {
                scale =
                        present.stream()
                                .map(BigDecimal::new)
                                .mapToInt(n -> Math.max(0, n.scale()))
                                .max()
                                .orElse(0);
                final int decimalScale = scale;
                if (scale > 18
                        || present.stream()
                                .map(BigDecimal::new)
                                .anyMatch(n -> n.precision() - n.scale() > 38 - decimalScale))
                    type = "VARCHAR";
            }
        }
        if ("VARCHAR".equals(type) && values.stream().anyMatch(s -> s.length() > 255))
            type = "TEXT";
        return CsvColumnDto.builder()
                .index(index)
                .name(name)
                .type(type)
                .length(255)
                .precision(38)
                .scale(scale)
                .build();
    }

    private String kind(String text) {
        if (Set.of("true", "false").contains(text.toLowerCase(Locale.ROOT))) return "BOOLEAN";
        if (text.matches("[+-]?[0-9]+")) {
            String digits = text.replaceFirst("^[+-]", "");
            if (digits.length() > 15 || digits.length() > 1 && digits.startsWith("0"))
                return "VARCHAR";
            try {
                Long.parseLong(text);
                return "BIGINT";
            } catch (NumberFormatException ignored) {
                return "VARCHAR";
            }
        }
        if (text.matches("[+-]?[0-9]+[.][0-9]+")) return "DECIMAL";
        try {
            if (text.length() == 10) {
                DateTimeUtils.parseMysqlDate(text);
                return "DATE";
            }
            if (text.length() == 19) {
                DateTimeUtils.parseMysqlDateTime(text);
                return "DATETIME";
            }
        } catch (RuntimeException ignored) {
            // Ambiguous or invalid dates remain text.
        }
        return "VARCHAR";
    }
}

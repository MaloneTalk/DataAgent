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

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Converts a JDBC column value to text; CSV escaping is handled by the writer. */
@Component
public class JdbcCsvValueConverter {

    public String convert(ResultSet rs, ResultSetMetaData metaData, int column)
            throws SQLException, IOException {
        return switch (metaData.getColumnType(column)) {
            case Types.NULL -> "";
            case Types.BOOLEAN,
                    Types.BIT,
                    Types.TINYINT,
                    Types.SMALLINT,
                    Types.INTEGER,
                    Types.BIGINT,
                    Types.REAL,
                    Types.FLOAT,
                    Types.DOUBLE,
                    Types.NUMERIC,
                    Types.DECIMAL,
                    Types.CHAR,
                    Types.VARCHAR,
                    Types.LONGVARCHAR,
                    Types.NCHAR,
                    Types.NVARCHAR,
                    Types.LONGNVARCHAR,
                    Types.DATE,
                    Types.TIME,
                    Types.TIMESTAMP,
                    Types.TIME_WITH_TIMEZONE,
                    Types.TIMESTAMP_WITH_TIMEZONE ->
                    Objects.toString(rs.getString(column), "");
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                try (InputStream stream = rs.getBinaryStream(column)) {
                    // A prefix distinguishes empty binary data from NULL and avoids CSV formulas.
                    yield stream == null
                            ? ""
                            : "0x" + HexFormat.of().formatHex(stream.readAllBytes());
                }
            }
            case Types.CLOB, Types.NCLOB -> {
                try (Reader reader =
                        metaData.getColumnType(column) == Types.NCLOB
                                ? rs.getNCharacterStream(column)
                                : rs.getCharacterStream(column)) {
                    if (reader == null) yield "";
                    StringWriter text = new StringWriter();
                    reader.transferTo(text);
                    yield text.toString();
                }
            }
            default ->
                    throw BusinessException.of(
                            ErrorCode.BAD_REQUEST,
                            "CSV export does not support JDBC type "
                                    + metaData.getColumnTypeName(column)
                                    + " for column "
                                    + metaData.getColumnLabel(column)
                                    + ". Cast it to a supported scalar type before exporting.");
        };
    }
}

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

import io.github.malonetalk.dto.datasource.PhysicalColumnInfo;
import io.github.malonetalk.utils.SemanticUtils;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JdbcColumnMetadataConverter {

    /** Maps the current metadata row using normalized primary-key and index column names. */
    public PhysicalColumnInfo convert(
            ResultSet rs, List<String> primaryKeys, Map<String, List<String>> indexesByColumn)
            throws SQLException {
        String columnName = rs.getString("COLUMN_NAME");
        String normalizedColumnName =
                SemanticUtils.normalizeObjectName(
                        columnName, "Missing column name while reading schema.");
        return PhysicalColumnInfo.builder()
                .columnName(columnName)
                .typeName(rs.getString("TYPE_NAME"))
                .columnSize(rs.getInt("COLUMN_SIZE"))
                .decimalDigits(rs.getInt("DECIMAL_DIGITS"))
                .nullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")))
                .defaultValue(rs.getString("COLUMN_DEF"))
                .primaryKey(primaryKeys.contains(normalizedColumnName))
                .remarks(rs.getString("REMARKS"))
                .indexes(indexesByColumn.getOrDefault(normalizedColumnName, List.of()))
                .build();
    }
}

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
package io.github.malonetalk.dto.datasource;

/** 物理数据源列信息，由 SchemaReader 从 JDBC 元数据读取 */
public record ColumnInfo(
        String columnName,
        String typeName,
        int columnSize,
        boolean nullable,
        String defaultValue,
        boolean primaryKey,
        String remarks) {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(columnName).append(" ").append(typeName);
        if (columnSize > 0) {
            sb.append("(").append(columnSize).append(")");
        }
        if (primaryKey) {
            sb.append(" PRIMARY KEY");
        }
        if (!nullable) {
            sb.append(" NOT NULL");
        }
        if (defaultValue != null && !defaultValue.isEmpty()) {
            sb.append(" DEFAULT ").append(defaultValue);
        }
        if (remarks != null && !remarks.isEmpty()) {
            sb.append(" COMMENT '").append(remarks).append("'");
        }
        return sb.toString();
    }
}

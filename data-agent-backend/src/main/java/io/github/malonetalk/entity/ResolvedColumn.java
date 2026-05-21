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
package io.github.malonetalk.entity;

import java.time.LocalDateTime;

/**
 * 已解析的列信息，合并了物理列和语义列的数据
 */
public record ResolvedColumn(
        Integer semanticId,
        String tableName,
        String columnName,
        io.github.malonetalk.agent.datasource.ColumnInfo physicalColumn,
        ColumnInfo semanticColumn,
        String description,
        String physicalDescription,
        String typeName,
        String typeText,
        boolean primaryKey,
        boolean nullable,
        String defaultValue,
        LocalDateTime updateTime,
        boolean visible) {

    public boolean hasPhysicalColumn() {
        return physicalColumn != null;
    }
}

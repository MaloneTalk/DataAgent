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
 * 已解析的表信息，合并了物理表和语义表的数据
 */
public record ResolvedTable(
        Integer semanticId,
        String canonicalName,
        TableInfo physicalTable,
        TableInfo semanticTable,
        String domain,
        String description,
        String physicalDescription,
        LocalDateTime updateTime,
        boolean visible) {

    public boolean hasPhysicalTable() {
        return physicalTable != null;
    }
}

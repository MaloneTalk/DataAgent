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
package io.github.malonetalk.dto.semantic;

import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record ColumnSemanticResponse(
        Integer id,
        String columnName,
        String physicalColumnDescription,
        String columnDescription,
        String typeName,
        Boolean primaryKey,
        String indexInfo,
        Boolean isVisible,
        Boolean hasPhysicalColumn,
        // 最终可用性（推导值，非存储字段）：effective = isVisible && hasPhysicalColumn。
        // 与 isVisible 含义不同——isVisible 只表示用户是否手动隐藏该列；
        // effective 还需物理列依然存在，二者任一为假即为 false。
        // 它由 SemanticConverter 调 SemanticAvailabilityHelper.isColumnAvailable(USER_OPERATION) 计算，
        // 前端据此渲染“有效/无效”徽标。
        Boolean effective,
        String invalidReason,
        LocalDateTime updateTime) {}

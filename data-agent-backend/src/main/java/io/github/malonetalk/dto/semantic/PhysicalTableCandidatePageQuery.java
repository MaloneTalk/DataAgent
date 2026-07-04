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

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PhysicalTableCandidatePageQuery(
        @NotNull(message = "datasourceId 不能为空") Integer datasourceId,
        @Min(value = 1, message = "page 不能小于 1") Integer page,
        @Min(value = 1, message = "pageSize 不能小于 1") Integer pageSize,
        String keyword,
        String sortOrder) {}

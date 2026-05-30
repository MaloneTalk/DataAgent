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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateLogicalTableRelationRequest(
        @NotEmpty(message = "sourceColumnNames 不能为空") List<String> sourceColumnNames,
        @NotBlank(message = "targetTableName 不能为空") String targetTableName,
        @NotEmpty(message = "targetColumnNames 不能为空") List<String> targetColumnNames,
        @Size(max = 1000, message = "description 长度不能超过 1000") String description,
        @NotNull(message = "enabled 不能为空") Boolean enabled) {}

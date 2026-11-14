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
package io.github.malonetalk.model.dto;

import io.github.malonetalk.annotation.MaxBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UserCreateDto(
        @NotBlank(message = "username 不能为空") String username,
        @NotBlank(message = "password 不能为空")
                @Size(min = 6, message = "password 长度至少 6 位")
                @MaxBytes(value = 72, message = "password 不能超过 72 字节")
                String password,
        @NotBlank(message = "displayName 不能为空") String displayName,
        @NotNull @PositiveOrZero(message = "roleId 不能为负") Integer roleId) {}

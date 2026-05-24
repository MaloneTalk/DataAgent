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
package io.github.malonetalk.common;

public record ToolResult<T>(boolean success, T data, ToolError error) {

    private static final String DEFAULT_ERROR_MESSAGE = "Tool execution failed.";

    public static <T> ToolResult<T> success(T data) {
        return new ToolResult<>(true, data, null);
    }

    public static <T> ToolResult<T> error(String code, String message) {
        String resolvedMessage =
                message == null || message.isBlank() ? DEFAULT_ERROR_MESSAGE : message;
        return new ToolResult<>(false, null, new ToolError(code, resolvedMessage));
    }
}

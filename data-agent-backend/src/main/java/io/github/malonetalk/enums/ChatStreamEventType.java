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
package io.github.malonetalk.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ChatStreamEventType {
    SUMMARY("summary"),
    TOOL_CALL("tool_call"),
    TOOL_RESULT("tool_result"),
    THINKING("thinking"),
    TEXT("text");

    private final String code;

    ChatStreamEventType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}

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
package io.github.malonetalk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.malonetalk.enums.ChatStreamEventType;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(
        ChatStreamEventType type,
        String messageId,
        boolean isLast,
        String content,
        ToolCallInfo toolCall,
        ToolResultInfo toolResult) {

    public record ToolCallInfo(String id, String name, Map<String, Object> input) {}

    public record ToolResultInfo(String id, String name, String output, boolean suspended) {

        public ToolResultInfo(String id, String name, String output) {
            this(id, name, output, false);
        }
    }
}

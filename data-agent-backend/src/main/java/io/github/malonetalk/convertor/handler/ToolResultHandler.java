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
package io.github.malonetalk.convertor.handler;

import io.agentscope.core.message.ToolResultBlock;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.enums.ChatStreamEventType;
import io.github.malonetalk.exception.ToolExceptionMapper;

public interface ToolResultHandler {

    boolean supports(ToolResultBlock block, String text);

    ChatStreamEvent handle(ToolResultBlock block, String text, String messageId, boolean isLast);

    static ChatStreamEvent defaultHandle(
            ToolResultBlock block, String text, String messageId, boolean isLast) {
        Object errorCode = block.getMetadata().get(ToolExceptionMapper.METADATA_ERROR_CODE);
        Object message = block.getMetadata().get(ToolExceptionMapper.METADATA_ERROR_MESSAGE);
        if (errorCode instanceof String code && message instanceof String msg) {
            return ChatStreamEvent.builder()
                    .type(ChatStreamEventType.ERROR)
                    .messageId(messageId)
                    .isLast(isLast)
                    .content(msg)
                    .errorCode(code)
                    .build();
        }
        return ChatStreamEvent.builder()
                .type(ChatStreamEventType.TOOL_RESULT)
                .messageId(messageId)
                .isLast(isLast)
                .toolResult(
                        new ChatStreamEvent.ToolResultInfo(
                                block.getId(), block.getName(), text, block.isSuspended()))
                .build();
    }
}

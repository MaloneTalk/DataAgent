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
import io.github.malonetalk.agent.tools.ToolCallConstants;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.ChatStreamEvent.ToolCallInfo;
import io.github.malonetalk.enums.ChatStreamEventType;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AskUserToolResultHandler implements ToolResultHandler {

    @Override
    public boolean supports(ToolResultBlock block, String text) {
        return block.isSuspended() && ToolCallConstants.ASK_USER.equals(block.getName());
    }

    @Override
    public ChatStreamEvent handle(
            ToolResultBlock block, String text, String messageId, boolean isLast) {
        return new ChatStreamEvent(
                ChatStreamEventType.QUESTION,
                messageId,
                isLast,
                text,
                new ToolCallInfo(block.getId(), block.getName(), Map.of()),
                null);
    }
}

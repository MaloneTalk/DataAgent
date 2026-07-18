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
package io.github.malonetalk.agent.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.github.malonetalk.exception.ExceptionResponseMapper;
import io.github.malonetalk.exception.ExceptionResponseMapper.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ToolExceptionMapper {

    private final ExceptionResponseMapper exceptionResponseMapper;
    private final ObjectMapper objectMapper;

    public ToolResultBlock toToolResult(Exception exception) {
        ErrorResponse errorResponse = exceptionResponseMapper.resolve(exception);
        return ToolResultBlock.error(toPayload(errorResponse));
    }

    private String toPayload(ErrorResponse errorResponse) {
        ToolErrorPayload payload =
                new ToolErrorPayload(errorResponse.errorCode().getCode(), errorResponse.message());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return errorResponse.errorCode().getCode() + ": " + errorResponse.message();
        }
    }

    private record ToolErrorPayload(String errorCode, String message) {}
}

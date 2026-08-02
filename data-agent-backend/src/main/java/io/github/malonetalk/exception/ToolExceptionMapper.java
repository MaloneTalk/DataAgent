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
package io.github.malonetalk.exception;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolSuspendException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 将 agent tool 内部异常转换为结构化 ToolResultBlock.error，避免工具异常打断整条 SSE 流。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolExceptionMapper {

    public static final String METADATA_ERROR_CODE = "errorCode";
    public static final String METADATA_ERROR_MESSAGE = "message";

    private final ExceptionResponseMapper exceptionResponseMapper;

    public ToolResultBlock run(String actionName, ToolAction action) {
        try {
            return action.run();
        } catch (ToolSuspendException exception) {
            // 挂起不是错误：交给 agentscope 走 ask_user/ask_caliber 恢复流程，吞掉会破坏交互语义。
            throw exception;
        } catch (Exception exception) {
            ErrorResponse errorResponse = exceptionResponseMapper.resolve(exception);
            if (errorResponse.isServerError()) {
                log.error("Tool action failed: {}", actionName, exception);
            }
            return toToolError(errorResponse);
        }
    }

    private ToolResultBlock toToolError(ErrorResponse errorResponse) {
        return ToolResultBlock.of(
                TextBlock.builder().text(errorResponse.message()).build(),
                Map.of(
                        METADATA_ERROR_CODE,
                        errorResponse.errorCode().getCode(),
                        METADATA_ERROR_MESSAGE,
                        errorResponse.message()));
    }

    @FunctionalInterface
    public interface ToolAction {
        ToolResultBlock run() throws Exception;
    }
}

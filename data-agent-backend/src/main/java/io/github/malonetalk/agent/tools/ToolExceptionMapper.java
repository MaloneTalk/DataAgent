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

import io.agentscope.core.tool.ToolSuspendException;
import io.github.malonetalk.exception.ErrorResponse;
import io.github.malonetalk.exception.ExceptionResponseMapper;
import io.github.malonetalk.model.bo.ToolResultBo;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 将 agent tool 内部异常转换为带错误元数据的 ToolResultBo，避免异常打断整条 SSE 流。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolExceptionMapper {

    public static final String METADATA_ERROR_CODE = "errorCode";
    public static final String METADATA_ERROR_MESSAGE = "message";

    private final ExceptionResponseMapper exceptionResponseMapper;

    public ToolResultBo runResult(ToolAction action) {
        try {
            return action.run();
        } catch (ToolSuspendException exception) {
            // 挂起不是错误：交给 agentscope 走 ask_user/ask_caliber 恢复流程，吞掉会破坏交互语义。
            throw exception;
        } catch (Exception exception) {
            ErrorResponse errorResponse = exceptionResponseMapper.resolve(exception);
            exceptionResponseMapper.logMapped(log, exception, errorResponse);
            return toToolError(errorResponse);
        }
    }

    private ToolResultBo toToolError(ErrorResponse errorResponse) {
        return new ToolResultBo(
                errorResponse.message(),
                Map.of(
                        METADATA_ERROR_CODE,
                        errorResponse.errorCode().getCode(),
                        METADATA_ERROR_MESSAGE,
                        errorResponse.message()));
    }

    @FunctionalInterface
    public interface ToolAction {
        ToolResultBo run() throws Exception;
    }
}

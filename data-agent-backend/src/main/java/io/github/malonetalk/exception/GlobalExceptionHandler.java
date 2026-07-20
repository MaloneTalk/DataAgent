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

import io.github.malonetalk.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class
    })
    public Result<Boolean> handleBadRequest(Exception exception) {
        String message =
                StringUtils.hasText(exception.getMessage())
                        ? exception.getMessage()
                        : "Bad request";
        return Result.error(400, message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Boolean> handleInternalError(Exception exception) {
        // 带状态码的异常（如 404/405/409 的 ResponseStatusException）直接透传，
        // 交给 Spring 默认的 ResponseStatusExceptionHandler 返回正确状态码，避免被误包成 500
        if (exception instanceof ResponseStatusException rse) {
            throw rse;
        }
        // MDC 中的 traceId 由 TraceIdFilter 注入，错误日志自动携带，便于按 traceId 串联排查
        log.error("Unhandled server error", exception);
        String message =
                StringUtils.hasText(exception.getMessage())
                        ? exception.getMessage()
                        : "Internal server error";
        return Result.error(500, message);
    }
}

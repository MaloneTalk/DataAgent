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

import io.github.malonetalk.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 将已知异常统一映射为 ErrorCode 和对外提示，供 HTTP、SSE、agent tool 共用。 */
@Component
@Slf4j
public class ExceptionResponseMapper {

    /** 统一异常映射入口；沿 cause 链逐层识别，被框架包装过的异常仍能保留原 ErrorCode。 */
    public ErrorResponse resolve(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            ErrorResponse mapped = map(current);
            if (mapped != null) {
                return mapped;
            }
        }
        return of(ErrorCode.INTERNAL_ERROR);
    }

    /** 识别单层异常；认不出返回 null，由 resolve 继续沿 cause 链查找。 */
    private ErrorResponse map(Throwable current) {
        if (current instanceof BusinessException businessException) {
            return fromBusinessException(businessException);
        }
        if (current instanceof IllegalArgumentException) {
            // 未迁移的裸参数断言统一视为请求参数错误，避免落成 500；
            // 原始 message 可能含连接串等内部细节，只进日志不进响应体。
            log.debug("IllegalArgumentException mapped to BAD_REQUEST: {}", current.getMessage());
            return of(ErrorCode.BAD_REQUEST);
        }
        if (current instanceof DataIntegrityViolationException) {
            return of(ErrorCode.DATA_CONFLICT);
        }
        if (current instanceof TransientDataAccessException) {
            return of(ErrorCode.DATA_SERVICE_UNAVAILABLE);
        }
        if (current instanceof DataAccessException) {
            return of(ErrorCode.DATA_ACCESS_FAILED);
        }
        if (current instanceof NoResourceFoundException) {
            return of(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (current instanceof HttpRequestMethodNotSupportedException) {
            return of(ErrorCode.METHOD_NOT_ALLOWED);
        }
        if (current instanceof HttpMediaTypeNotSupportedException) {
            return of(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        if (current instanceof HttpMediaTypeNotAcceptableException) {
            return of(ErrorCode.NOT_ACCEPTABLE);
        }
        return null;
    }

    /** 使用错误码默认文案构造响应错误信息。 */
    public ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getDefaultMessage());
    }

    /** 使用调用方指定文案构造响应错误信息，空文案回退到错误码默认文案。 */
    public ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode, defaultIfBlank(message, errorCode.getDefaultMessage()));
    }

    /** 保留业务异常携带的错误码，同时统一处理空 message 的回退。 */
    private ErrorResponse fromBusinessException(BusinessException exception) {
        return of(exception.getErrorCode(), exception.getMessage());
    }

    /** 统一日志策略：5xx 记完整堆栈，4xx 记一行摘要；HTTP、SSE、tool 三出口共用。 */
    public void logMapped(Logger logger, Throwable exception, ErrorResponse errorResponse) {
        if (errorResponse.isServerError()) {
            logger.error("Mapped server exception", exception);
        } else {
            logger.warn(
                    "Mapped client error: {} - {}",
                    errorResponse.errorCode(),
                    errorResponse.message());
        }
    }

    /** 对外错误文案不能是空值，避免前端拿到不可展示的 message。 */
    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

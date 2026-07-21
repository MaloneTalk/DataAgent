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
public class ExceptionResponseMapper {

    /** 统一异常映射入口；先拆包业务异常，再按基础设施异常类型兜底映射。 */
    public ErrorResponse resolve(Throwable exception) {
        BusinessException businessException = findBusinessException(exception);
        if (businessException != null) {
            return fromBusinessException(businessException);
        }
        if (exception instanceof DataIntegrityViolationException) {
            return of(ErrorCode.DATA_CONFLICT);
        }
        if (exception instanceof TransientDataAccessException) {
            return of(ErrorCode.DATA_SERVICE_UNAVAILABLE);
        }
        if (exception instanceof DataAccessException) {
            return of(ErrorCode.DATA_ACCESS_FAILED);
        }
        if (exception instanceof NoResourceFoundException) {
            return of(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (exception instanceof HttpRequestMethodNotSupportedException) {
            return of(ErrorCode.METHOD_NOT_ALLOWED);
        }
        if (exception instanceof HttpMediaTypeNotSupportedException) {
            return of(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        if (exception instanceof HttpMediaTypeNotAcceptableException) {
            return of(ErrorCode.NOT_ACCEPTABLE);
        }
        return of(ErrorCode.INTERNAL_ERROR);
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

    /** 对外错误文案不能是空值，避免前端拿到不可展示的 message。 */
    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** 沿 cause 链查找业务异常，让被框架或第三方库包装过的错误仍能保留原 ErrorCode。 */
    private BusinessException findBusinessException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof BusinessException businessException) {
                return businessException;
            }
            current = current.getCause();
        }
        return null;
    }
}

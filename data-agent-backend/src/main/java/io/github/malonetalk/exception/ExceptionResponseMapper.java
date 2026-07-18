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

import io.github.malonetalk.agent.datasource.SchemaReader.SchemaReadException;
import io.github.malonetalk.agent.datasource.SqlExecutor.SqlExecutionException;
import io.github.malonetalk.agent.datasource.SqlExecutor.SqlSecurityException;
import io.github.malonetalk.common.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

@Component
public class ExceptionResponseMapper {

    public ErrorResponse resolve(Throwable exception) {
        if (exception instanceof BusinessException businessException) {
            return fromBusinessException(businessException);
        }
        if (exception instanceof SqlSecurityException) {
            return of(ErrorCode.SQL_NOT_ALLOWED, exception.getMessage());
        }
        if (exception instanceof SchemaReadException) {
            return of(ErrorCode.SCHEMA_READ_FAILED);
        }
        if (exception instanceof SqlExecutionException) {
            return of(ErrorCode.SQL_EXECUTION_FAILED);
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
        return of(ErrorCode.INTERNAL_ERROR);
    }

    public ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getDefaultMessage());
    }

    public ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode, defaultIfBlank(message, errorCode.getDefaultMessage()));
    }

    private ErrorResponse fromBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode != null) {
            return of(errorCode, exception.getMessage());
        }
        return of(fallbackBusinessCode(exception), exception.getMessage());
    }

    private ErrorCode fallbackBusinessCode(BusinessException exception) {
        return switch (exception.getStatus()) {
            case BAD_REQUEST -> ErrorCode.BAD_REQUEST;
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case CONFLICT -> ErrorCode.DATA_CONFLICT;
            case SERVICE_UNAVAILABLE -> ErrorCode.DATA_SERVICE_UNAVAILABLE;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public record ErrorResponse(ErrorCode errorCode, String message) {}
}

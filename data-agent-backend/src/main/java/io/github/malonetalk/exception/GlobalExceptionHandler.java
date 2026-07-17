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
import io.github.malonetalk.common.Result;
import io.github.malonetalk.dto.FieldValidationError;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ExceptionMessageResolver exceptionMessageResolver;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusinessException(BusinessException exception) {
        return response(
                exception.getStatus(), exceptionMessageResolver.resolveClientMessage(exception));
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        ServletRequestBindingException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Object>> handleBadRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, resolveBadRequestMessage(exception));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        return validationResponse(toFieldValidationErrors(exception.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Object>> handleBindException(BindException exception) {
        return validationResponse(toFieldValidationErrors(exception.getBindingResult()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Object>> handleConstraintViolation(
            ConstraintViolationException exception) {
        List<FieldValidationError> errors =
                exception.getConstraintViolations().stream()
                        .map(
                                violation ->
                                        new FieldValidationError(
                                                resolveConstraintField(
                                                        violation.getPropertyPath().toString()),
                                                violation.getMessage()))
                        .toList();
        return validationResponse(errors);
    }

    @ExceptionHandler(SqlSecurityException.class)
    public ResponseEntity<Result<Object>> handleSqlSecurityException(
            SqlSecurityException exception) {
        return response(
                HttpStatus.BAD_REQUEST, exceptionMessageResolver.resolveClientMessage(exception));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Object>> handleResourceNotFound() {
        return response(HttpStatus.NOT_FOUND, "Resource not found.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Object>> handleMethodNotSupported() {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "Request method is not supported.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Object>> handleMediaTypeNotSupported() {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media type is not supported.");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<Object>> handleMediaTypeNotAcceptable() {
        return response(
                HttpStatus.NOT_ACCEPTABLE, "No acceptable response media type is available.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Object>> handleDataConflict(
            DataIntegrityViolationException exception) {
        log.warn("Data integrity conflict: {}", exception.getMessage());
        return response(
                HttpStatus.CONFLICT, exceptionMessageResolver.resolveClientMessage(exception));
    }

    @ExceptionHandler(TransientDataAccessException.class)
    public ResponseEntity<Result<Object>> handleTransientDataAccess(
            TransientDataAccessException exception) {
        log.error("Transient data access exception", exception);
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                exceptionMessageResolver.resolveClientMessage(exception));
    }

    @ExceptionHandler({
        SchemaReadException.class,
        SqlExecutionException.class,
        DataAccessException.class
    })
    public ResponseEntity<Result<Object>> handleInfrastructureException(Exception exception) {
        log.error("Infrastructure exception", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                exceptionMessageResolver.resolveClientMessage(exception));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.");
    }

    private String resolveBadRequestMessage(Exception exception) {
        if (exception instanceof MethodArgumentTypeMismatchException mismatchException) {
            return "Invalid value for request parameter '" + mismatchException.getName() + "'.";
        }
        if (exception instanceof MissingServletRequestParameterException missingException) {
            return "Required request parameter '"
                    + missingException.getParameterName()
                    + "' is missing.";
        }
        if (exception instanceof ServletRequestBindingException) {
            return "Invalid request binding.";
        }
        if (exception instanceof MethodArgumentNotValidException validationException) {
            return validationException.getBindingResult().getAllErrors().stream()
                    .findFirst()
                    .map(error -> error.getDefaultMessage())
                    .orElse("Invalid request parameters.");
        }
        if (exception instanceof BindException bindException) {
            return bindException.getAllErrors().stream()
                    .findFirst()
                    .map(error -> error.getDefaultMessage())
                    .orElse("Invalid request parameters.");
        }
        if (exception instanceof ConstraintViolationException violationException) {
            return violationException.getConstraintViolations().stream()
                    .findFirst()
                    .map(violation -> violation.getMessage())
                    .orElse("Invalid request parameters.");
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return "Malformed request body.";
        }
        return exception.getMessage() == null
                ? "Invalid request parameters."
                : exception.getMessage();
    }

    private ResponseEntity<Result<Object>> validationResponse(List<FieldValidationError> errors) {
        String message =
                errors.stream()
                        .findFirst()
                        .map(FieldValidationError::message)
                        .orElse("Invalid request parameters.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(HttpStatus.BAD_REQUEST, message, errors));
    }

    private List<FieldValidationError> toFieldValidationErrors(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream().map(this::toFieldValidationError).toList();
    }

    private FieldValidationError toFieldValidationError(ObjectError error) {
        String field =
                error instanceof FieldError fieldError
                        ? fieldError.getField()
                        : error.getObjectName();
        String message =
                error.getDefaultMessage() == null
                        ? "Invalid request parameters."
                        : error.getDefaultMessage();
        return new FieldValidationError(field, message);
    }

    private String resolveConstraintField(String propertyPath) {
        int separatorIndex = propertyPath.lastIndexOf('.');
        if (separatorIndex < 0 || separatorIndex == propertyPath.length() - 1) {
            return propertyPath;
        }
        return propertyPath.substring(separatorIndex + 1);
    }

    private ResponseEntity<Result<Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Result.error(status, message));
    }
}

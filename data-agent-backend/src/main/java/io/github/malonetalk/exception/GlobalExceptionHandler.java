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
import io.github.malonetalk.common.Result;
import io.github.malonetalk.dto.FieldValidationError;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** HTTP 全局异常处理入口，负责把异常转换成带 errorCode 的统一 Result 响应。 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ExceptionResponseMapper exceptionResponseMapper;

    /** Spring 请求解析类错误默认是 BAD_REQUEST，但先允许 mapper 识别其中包装的业务异常。 */
    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        ServletRequestBindingException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Object>> handleBadRequest(Exception exception) {
        ErrorResponse errorResponse = exceptionResponseMapper.resolve(exception);
        if (errorResponse.errorCode() != ErrorCode.INTERNAL_ERROR) {
            return response(errorResponse);
        }
        return response(ErrorCode.BAD_REQUEST, resolveBadRequestMessage(exception));
    }

    /** Bean Validation 的字段错误返回 VALIDATION_FAILED，并把字段错误放入 data。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        return validationResponse(toFieldValidationErrors(exception.getBindingResult()));
    }

    /** 表单或查询参数绑定错误同样按字段校验失败返回。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Object>> handleBindException(BindException exception) {
        return validationResponse(toFieldValidationErrors(exception.getBindingResult()));
    }

    /** 方法参数上的约束校验失败需要从 propertyPath 中提取最后一级字段名。 */
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(Exception exception) {
        ErrorResponse errorResponse = exceptionResponseMapper.resolve(exception);
        logMappedException(exception, errorResponse);
        return response(errorResponse);
    }

    /** 为 Spring 请求解析异常提供稳定、不过度泄露内部细节的 BAD_REQUEST 文案。 */
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
        if (exception instanceof HttpMessageNotReadableException) {
            return "Malformed request body.";
        }
        return exception.getMessage() == null
                ? "Invalid request parameters."
                : exception.getMessage();
    }

    /** 字段校验响应使用第一条错误作为主 message，完整字段错误列表放在 data。 */
    private ResponseEntity<Result<Object>> validationResponse(List<FieldValidationError> errors) {
        String message =
                errors.stream()
                        .findFirst()
                        .map(FieldValidationError::message)
                        .orElse("Invalid request parameters.");
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(Result.error(ErrorCode.VALIDATION_FAILED, message, errors));
    }

    /** 将 Spring BindingResult 转为前端稳定消费的字段错误结构。 */
    private List<FieldValidationError> toFieldValidationErrors(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream().map(this::toFieldValidationError).toList();
    }

    /** FieldError 使用字段名，ObjectError 回退到对象名。 */
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

    /** ConstraintViolation 的路径可能带方法名或对象名前缀，这里只保留最后一级字段。 */
    private String resolveConstraintField(String propertyPath) {
        int separatorIndex = propertyPath.lastIndexOf('.');
        if (separatorIndex < 0 || separatorIndex == propertyPath.length() - 1) {
            return propertyPath;
        }
        return propertyPath.substring(separatorIndex + 1);
    }

    private void logMappedException(Exception exception, ErrorResponse errorResponse) {
        if (errorResponse.isServerError()) {
            log.error("Mapped server exception", exception);
        }
    }

    /** 按错误码和指定文案组装 HTTP 响应。 */
    private ResponseEntity<Result<Object>> response(ErrorCode errorCode, String message) {
        return response(exceptionResponseMapper.of(errorCode, message));
    }

    /** 最终响应出口：HTTP 状态码、业务 errorCode 和 message 在这里对齐。 */
    private ResponseEntity<Result<Object>> response(ErrorResponse errorResponse) {
        ErrorCode errorCode = errorResponse.errorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.error(errorCode, errorResponse.message(), null));
    }
}

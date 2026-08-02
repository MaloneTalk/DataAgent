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
package io.github.malonetalk.common;

import java.io.Serializable;
import lombok.Data;
import org.springframework.http.HttpStatus;

/** 统一 API 响应体；错误响应通过 errorCode 暴露稳定业务码，message 暴露展示文案。 */
@Data
public class Result<T> implements Serializable {

    private Integer code;
    private String errorCode;
    private String message;
    private T data;

    public Result() {}

    public Result(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public Result(Integer code, String errorCode, String message, T data) {
        this.code = code;
        this.errorCode = errorCode;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>(HttpStatus.OK.value(), "success");
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(HttpStatus.OK.value(), "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 错误响应统一从 ErrorCode 派生 HTTP code 和业务 errorCode。 */
    public static <T> Result<T> error(ErrorCode errorCode, String message, T data) {
        Result<T> result =
                new Result<>(errorCode.getHttpStatus().value(), errorCode.getCode(), message, data);
        return result;
    }
}

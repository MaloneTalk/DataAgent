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
package io.github.malonetalk.model.vo;

import io.github.malonetalk.exception.ErrorCode;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultVo<T extends BaseVo> implements Serializable {

    private Integer code;
    private String errorCode;
    private String message;
    private T data;

    public static <T extends BaseVo> ResultVo<T> success(T data) {
        return new ResultVo<>(HttpStatus.OK.value(), null, "success", data);
    }

    public static <T extends BaseVo> ResultVo<T> success() {
        return new ResultVo<>(HttpStatus.OK.value(), null, "success", null);
    }

    /** 错误响应统一从 ErrorCode 派生 HTTP code 和业务 errorCode，data 恒为 null。 */
    public static <T extends BaseVo> ResultVo<T> error(ErrorCode errorCode, String message) {
        return new ResultVo<>(
                errorCode.getHttpStatus().value(), errorCode.getCode(), message, null);
    }
}

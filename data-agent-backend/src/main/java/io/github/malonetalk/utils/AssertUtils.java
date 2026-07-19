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
package io.github.malonetalk.utils;

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.exception.BusinessException;
import java.util.Collection;

public final class AssertUtils {

    private AssertUtils() {}

    public static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    public static void requireNonNegative(Integer value, String message) {
        if (value == null || value < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    public static String requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    public static void requireNotEmpty(Collection<?> value, String message) {
        if (value == null || value.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }
}

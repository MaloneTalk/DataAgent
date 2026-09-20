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
package io.github.malonetalk.model.holder;

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;

public interface ThreadLocalHolder<T> {

    void set(T obj);

    T get();

    void clear();

    default T checkAndGet() {
        T obj = get();
        if (obj == null) {
            throw BusinessException.of(ErrorCode.VALIDATION_FAILED);
        }
        return obj;
    }
}

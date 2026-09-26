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

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@AllArgsConstructor
public class JsonUtil {
    private final ObjectMapper objectMapper;

    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw BusinessException.of(ErrorCode.INTERNAL_ERROR, "json serialize exception.", e);
        }
    }

    public <T> T toObj(String json, TypeReference<T> ref) {
        try {
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            throw BusinessException.of(ErrorCode.INTERNAL_ERROR, "json deserialize exception.", e);
        }
    }
}

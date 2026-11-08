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
package io.github.malonetalk.model.bo;

import java.util.Map;

/** Result returned by an AgentScope tool before it is converted to a ToolResultBlock. */
public record ToolResultBo(String text, Map<String, Object> metadata) {

    public ToolResultBo {
        text = text == null ? "" : text;
        metadata = metadata == null ? Map.of() : metadata;
    }

    public static ToolResultBo of(String text) {
        return new ToolResultBo(text, Map.of());
    }
}

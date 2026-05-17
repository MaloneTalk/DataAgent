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
package io.github.malonetalk.convertor;

import io.github.malonetalk.dto.McpServerRequest;
import io.github.malonetalk.dto.McpServerResponse;
import io.github.malonetalk.entity.McpServer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface McpServerConverter {

    @Mapping(
            target = "args",
            expression = "java(request.args() != null ? String.join(\",\", request.args()) : null)")
    @Mapping(target = "env", ignore = true)
    @Mapping(target = "headers", ignore = true)
    @Mapping(target = "queryParams", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    McpServer toEntity(McpServerRequest request);

    @Mapping(
            target = "args",
            expression =
                    "java(mcpServer.getArgs() != null ?"
                            + " java.util.List.of(mcpServer.getArgs().split(\",\")) : null)")
    @Mapping(target = "env", expression = "java(null)")
    @Mapping(target = "headers", expression = "java(null)")
    @Mapping(target = "queryParams", expression = "java(null)")
    McpServerResponse toResponse(McpServer mcpServer);
}

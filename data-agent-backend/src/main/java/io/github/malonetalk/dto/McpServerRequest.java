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
package io.github.malonetalk.dto;

import io.github.malonetalk.enums.ClientType;
import io.github.malonetalk.enums.HttpVersion;
import io.github.malonetalk.enums.RedirectPolicy;
import io.github.malonetalk.enums.TransportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record McpServerRequest(
        @NotBlank(message = "name 不能为空") String name,
        @NotNull(message = "transportType 不能为空") TransportType transportType,
        @NotNull(message = "clientType 不能为空") ClientType clientType,
        String command,
        List<String> args,
        Map<String, String> env,
        String url,
        Map<String, String> headers,
        Map<String, String> queryParams,
        Long timeout,
        Long initializationTimeout,
        Boolean enableElicitation,
        HttpVersion httpVersion,
        Long connectTimeout,
        RedirectPolicy redirectPolicy,
        String description) {}

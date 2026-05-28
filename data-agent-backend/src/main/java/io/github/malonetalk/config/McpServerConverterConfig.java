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
package io.github.malonetalk.config;

import io.github.malonetalk.convertor.McpServerConverter;
import io.github.malonetalk.dto.McpServerRequest;
import io.github.malonetalk.dto.McpServerResponse;
import io.github.malonetalk.entity.McpServer;
import io.github.malonetalk.enums.HttpVersion;
import io.github.malonetalk.enums.RedirectPolicy;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class McpServerConverterConfig {

    @Bean
    @Primary
    public McpServerConverter mcpServerConverter() {
        return new McpServerConverter() {
            @Override
            public McpServer toEntity(McpServerRequest request) {
                if (request == null) {
                    return null;
                }

                McpServer mcpServer = new McpServer();
                mcpServer.setName(request.name());
                mcpServer.setTransportType(request.transportType());
                mcpServer.setClientType(request.clientType());
                mcpServer.setCommand(request.command());
                mcpServer.setArgs(
                        request.args() != null ? String.join(",", request.args()) : null);
                mcpServer.setUrl(request.url());
                mcpServer.setTimeout(request.timeout());
                mcpServer.setInitializationTimeout(request.initializationTimeout());
                mcpServer.setEnableElicitation(request.enableElicitation());
                mcpServer.setHttpVersion(
                        request.httpVersion() != null ? request.httpVersion().name() : null);
                mcpServer.setConnectTimeout(request.connectTimeout());
                mcpServer.setRedirectPolicy(
                        request.redirectPolicy() != null ? request.redirectPolicy().name() : null);
                mcpServer.setDescription(request.description());
                return mcpServer;
            }

            @Override
            public McpServerResponse toResponse(McpServer mcpServer) {
                if (mcpServer == null) {
                    return null;
                }

                return new McpServerResponse(
                        mcpServer.getId(),
                        mcpServer.getName(),
                        mcpServer.getTransportType(),
                        mcpServer.getClientType(),
                        mcpServer.getCommand(),
                        mcpServer.getArgs() != null
                                ? List.of(mcpServer.getArgs().split(","))
                                : null,
                        null,
                        mcpServer.getUrl(),
                        null,
                        null,
                        mcpServer.getTimeout(),
                        mcpServer.getInitializationTimeout(),
                        mcpServer.getEnableElicitation(),
                        mcpServer.getHttpVersion() != null
                                ? HttpVersion.valueOf(mcpServer.getHttpVersion())
                                : null,
                        mcpServer.getConnectTimeout(),
                        mcpServer.getRedirectPolicy() != null
                                ? RedirectPolicy.valueOf(mcpServer.getRedirectPolicy())
                                : null,
                        mcpServer.getStatus(),
                        mcpServer.getDescription(),
                        mcpServer.getCreateTime(),
                        mcpServer.getUpdateTime());
            }
        };
    }
}

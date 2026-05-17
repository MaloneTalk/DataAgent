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
package io.github.malonetalk.entity;

import io.github.malonetalk.enums.ClientType;
import io.github.malonetalk.enums.TransportType;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class McpServer {

    private Integer id;
    private String name;
    private TransportType transportType;
    private ClientType clientType;

    private String command;
    private String args;
    private String env;

    private String url;
    private String headers;
    private String queryParams;

    private Long timeout;
    private Long initializationTimeout;

    private Boolean enableElicitation;
    private String httpVersion;
    private Long connectTimeout;
    private String redirectPolicy;

    private String status;
    private String description;
    private Long creatorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

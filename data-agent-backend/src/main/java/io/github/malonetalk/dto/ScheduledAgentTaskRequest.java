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

import jakarta.validation.constraints.NotBlank;

public record ScheduledAgentTaskRequest(
        @NotBlank(message = "name cannot be blank.") String name,
        @NotBlank(message = "prompt cannot be blank.") String prompt,
        @NotBlank(message = "scheduleType cannot be blank.") String scheduleType,
        @NotBlank(message = "scheduleExpr cannot be blank.") String scheduleExpr,
        String timezone,
        Boolean enabled,
        String sessionMode,
        String sessionId) {}

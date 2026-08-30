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

public final class DashboardDtos {

    private DashboardDtos() {}

    public record DashboardCardCreateRequest(
            @NotBlank(message = "title cannot be blank.") String title,
            Integer datasourceId,
            @NotBlank(message = "sqlText cannot be blank.") String sqlText,
            @NotBlank(message = "chartType cannot be blank.") String chartType) {}

    public record DashboardCardResponse(
            Integer id, String title, Integer datasourceId, String sqlText, String chartType) {}
}

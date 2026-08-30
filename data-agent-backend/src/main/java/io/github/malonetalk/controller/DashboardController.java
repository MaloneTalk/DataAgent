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
package io.github.malonetalk.controller;

import io.github.malonetalk.common.Result;
import io.github.malonetalk.dto.DashboardDtos.DashboardCardCreateRequest;
import io.github.malonetalk.dto.DashboardDtos.DashboardCardRefreshResponse;
import io.github.malonetalk.dto.DashboardDtos.DashboardCardResponse;
import io.github.malonetalk.service.DashboardService;
import io.github.malonetalk.utils.RequestAssert;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard-cards")
    public Result<List<DashboardCardResponse>> listCards() {
        return Result.success(dashboardService.listCards());
    }

    @PostMapping("/dashboard-cards")
    public Result<DashboardCardResponse> createCard(
            @Valid @RequestBody DashboardCardCreateRequest request) {
        return Result.success(dashboardService.createCard(request));
    }

    @DeleteMapping("/dashboard-cards/{id}")
    public Result<Void> deleteCard(@PathVariable Integer id) {
        RequestAssert.requireNonNegative(id, "id must be non-negative.");
        dashboardService.deleteCard(id);
        return Result.success();
    }

    @PostMapping("/dashboard-cards/refresh")
    public Result<Map<Integer, DashboardCardRefreshResponse>> refreshCards(
            @RequestBody List<Integer> ids) {
        return Result.success(dashboardService.refreshCards(ids));
    }
}

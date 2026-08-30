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
package io.github.malonetalk.service;

import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.agent.datasource.SqlExecutor;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.UserContext;
import io.github.malonetalk.dto.DashboardDtos.DashboardCardCreateRequest;
import io.github.malonetalk.dto.DashboardDtos.DashboardCardResponse;
import io.github.malonetalk.entity.DashboardCard;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.DashboardMapper;
import io.github.malonetalk.utils.RequestAssert;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;
    private final DatasourceService datasourceService;
    private final SqlExecutor sqlExecutor;

    public List<DashboardCardResponse> listCards() {
        return dashboardMapper.selectCardsByCreator(currentUserId()).stream()
                .map(this::toCardResponse)
                .toList();
    }

    @Transactional
    public DashboardCardResponse createCard(DashboardCardCreateRequest request) {
        Integer userId = currentUserId();
        Datasource datasource = requireDatasource(request.datasourceId());
        String sql = sqlExecutor.validateSelectSql(request.sqlText());

        DashboardCard card = new DashboardCard();
        card.setTitle(RequestAssert.requireNotBlank(request.title(), "title cannot be blank."));
        card.setDatasourceId(datasource.getId());
        card.setSqlText(sql);
        card.setChartType(normalizeChartType(request.chartType()));
        card.setCreatorId(userId);
        if (dashboardMapper.insertCard(card) <= 0) {
            throw BusinessException.of(
                    ErrorCode.OPERATION_FAILED, "Failed to save dashboard card.");
        }
        return toCardResponse(card);
    }

    @Transactional
    public void deleteCard(Integer id) {
        if (dashboardMapper.deleteCard(id, currentUserId()) <= 0) {
            throw cardNotFound(id);
        }
    }

    public QueryResult refreshCard(Integer id) {
        DashboardCard card = requireCard(id);
        Datasource datasource = requireDatasource(card.getDatasourceId());
        return sqlExecutor.execute(datasource, card.getSqlText());
    }

    private DashboardCard requireCard(Integer id) {
        RequestAssert.requireNonNull(id, "card id cannot be null.");
        DashboardCard card = dashboardMapper.selectCardByIdAndCreator(id, currentUserId());
        if (card == null) {
            throw cardNotFound(id);
        }
        return card;
    }

    private Datasource requireDatasource(Integer id) {
        RequestAssert.requireNonNull(id, "datasourceId cannot be null.");
        Datasource datasource = datasourceService.findById(id);
        if (datasource == null) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "Datasource not found.");
        }
        return datasource;
    }

    private String normalizeChartType(String chartType) {
        String normalized = RequestAssert.requireNotBlank(chartType, "chartType cannot be blank.");
        normalized = normalized.toLowerCase();
        return switch (normalized) {
            case "table", "metric", "bar" -> normalized;
            default ->
                    throw BusinessException.of(
                            ErrorCode.BAD_REQUEST, "chartType must be table, metric or bar.");
        };
    }

    private Integer currentUserId() {
        return UserContext.require().userId();
    }

    private DashboardCardResponse toCardResponse(DashboardCard card) {
        return new DashboardCardResponse(
                card.getId(),
                card.getTitle(),
                card.getDatasourceId(),
                card.getSqlText(),
                card.getChartType());
    }

    private BusinessException cardNotFound(Integer id) {
        return BusinessException.of(
                ErrorCode.RESOURCE_NOT_FOUND, "Dashboard card does not exist: id=" + id);
    }
}

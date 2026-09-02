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

import io.github.malonetalk.agent.SessionService;
import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.agent.datasource.SqlExecutor;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.UserContext;
import io.github.malonetalk.dto.DashboardDtos.DashboardCardCreateRequest;
import io.github.malonetalk.dto.DashboardDtos.DashboardCardRefreshResponse;
import io.github.malonetalk.dto.DashboardDtos.DashboardCardResponse;
import io.github.malonetalk.entity.DashboardCard;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.DashboardMapper;
import io.github.malonetalk.utils.RequestAssert;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int MAX_REFRESH_CARDS = 10;

    private final DashboardMapper dashboardMapper;
    private final DatasourceService datasourceService;
    private final SessionService sessionService;
    private final SqlExecutor sqlExecutor;

    public List<DashboardCardResponse> listCards() {
        return dashboardMapper.selectCardsByCreator(currentUserId()).stream()
                .map(this::toCardResponse)
                .toList();
    }

    @Transactional
    public DashboardCardResponse createCard(DashboardCardCreateRequest request) {
        UserContext user = UserContext.require();
        sessionService.requireOwnership(user.scopedUserId(), request.sessionId());
        Datasource datasource = datasourceService.getDatasourceForSession(request.sessionId());
        String sql = sqlExecutor.validateSelectSql(request.sqlText());

        DashboardCard card = new DashboardCard();
        card.setTitle(RequestAssert.requireNotBlank(request.title(), "title cannot be blank."));
        card.setDatasourceId(datasource.getId());
        card.setSqlText(sql);
        card.setChartType(normalizeChartType(request.chartType()));
        card.setCreatorId(user.userId());
        if (dashboardMapper.insertCard(card) <= 0) {
            throw BusinessException.of(
                    ErrorCode.OPERATION_FAILED, "Failed to save dashboard card.");
        }
        return toCardResponse(card);
    }

    @Transactional
    public void deleteCard(Integer id) {
        if (dashboardMapper.deleteCard(id, currentUserId()) <= 0) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND, "Dashboard card does not exist: id=" + id);
        }
    }

    public Map<Integer, DashboardCardRefreshResponse> refreshCards(List<Integer> ids) {
        RequestAssert.requireNotEmpty(ids, "card ids cannot be empty.");
        ids.forEach(id -> RequestAssert.requireNonNegative(id, "id must be non-negative."));
        List<Integer> cardIds = ids.stream().distinct().toList();
        if (cardIds.size() > MAX_REFRESH_CARDS) {
            throw BusinessException.of(
                    ErrorCode.BAD_REQUEST,
                    "Cannot refresh more than " + MAX_REFRESH_CARDS + " dashboard cards.");
        }
        List<DashboardCard> cards =
                dashboardMapper.selectCardsByIdsAndCreator(cardIds, currentUserId());
        if (cards.size() != cardIds.size()) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND, "Dashboard card does not exist.");
        }

        Map<Integer, DashboardCardRefreshResponse> results = new LinkedHashMap<>();
        for (DashboardCard card : cards) {
            results.put(card.getId(), refreshCardSafely(card));
        }
        return results;
    }

    private DashboardCardRefreshResponse refreshCardSafely(DashboardCard card) {
        try {
            return new DashboardCardRefreshResponse(refreshCard(card), null);
        } catch (BusinessException e) {
            return new DashboardCardRefreshResponse(null, e.getMessage());
        }
    }

    private QueryResult refreshCard(DashboardCard card) {
        Datasource datasource = requireDatasource(card.getDatasourceId());
        return sqlExecutor.execute(datasource, card.getSqlText());
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
}

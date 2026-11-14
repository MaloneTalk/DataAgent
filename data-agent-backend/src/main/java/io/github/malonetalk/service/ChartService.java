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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.agent.datasource.SqlExecutor;
import io.github.malonetalk.agent.tools.ToolDatasourceResolver;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.bo.ChartPayloadBo;
import io.github.malonetalk.model.bo.ChartSpecBo;
import io.github.malonetalk.model.bo.QuerySnapshotBo;
import io.github.malonetalk.model.bo.SqlToolMetadataBo;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChartService {
    private static final Set<String> CHART_TYPES = Set.of("bar", "line", "pie", "scatter");
    private static final int MAX_SERIES = 20;
    private final ToolDatasourceResolver datasourceResolver;
    private final SqlExecutor sqlExecutor;
    private final ObjectMapper objectMapper;

    public ChartPayloadBo generate(
            String resultId, ChartSpecBo spec, List<Msg> messages, ToolCallContext context) {
        validateSpec(resultId, spec);
        ToolResultBlock source = findResult(resultId, messages);
        var snapshot =
                objectMapper.convertValue(
                        source.getMetadata().get(QuerySnapshotBo.METADATA_KEY),
                        QuerySnapshotBo.class);
        var metadata = objectMapper.convertValue(source.getMetadata(), SqlToolMetadataBo.class);
        var datasource = datasourceResolver.resolve(context, metadata.datasourceId());
        sqlExecutor.authorizeSelect(
                datasource,
                snapshot.originalSql(),
                context.userId(),
                Optional.ofNullable(metadata.authorizedColumns())
                        .orElseThrow(() -> BusinessException.of(ErrorCode.FORBIDDEN)));
        validateData(spec, snapshot.result());
        return new ChartPayloadBo(spec, snapshot.result().rows());
    }

    private ToolResultBlock findResult(String resultId, List<Msg> messages) {
        for (Msg message : messages) {
            if (message.getRole() != MsgRole.TOOL) continue;
            for (ContentBlock block : message.getContent()) {
                if (block instanceof ToolResultBlock result
                        && "execute_sql".equals(result.getName())
                        && result.getMetadata().get(QuerySnapshotBo.METADATA_KEY)
                                instanceof Map<?, ?> snapshot
                        && resultId.equals(snapshot.get("resultId"))) {
                    return result;
                }
            }
        }
        throw BusinessException.of(
                ErrorCode.BAD_REQUEST,
                "Query result not found in this conversation. Execute the SQL first and use its"
                        + " Result ID.");
    }

    private void validateSpec(String resultId, ChartSpecBo spec) {
        if (!StringUtils.hasText(resultId)
                || !StringUtils.hasText(spec.chartType())
                || !CHART_TYPES.contains(spec.chartType())
                || !StringUtils.hasText(spec.title())
                || !StringUtils.hasText(spec.xField())
                || CollectionUtils.isEmpty(spec.yFields())
                || spec.yFields().stream().anyMatch(field -> !StringUtils.hasText(field))) {
            throw invalid(
                    "Specify a result_id, chart_type (bar/line/pie/scatter), title, x_field and"
                            + " y_fields.");
        }
        if (new HashSet<>(spec.yFields()).size() != spec.yFields().size()) {
            throw invalid("y_fields must not contain duplicate fields.");
        }
        if ("pie".equals(spec.chartType())
                && (spec.yFields().size() != 1 || StringUtils.hasText(spec.seriesField()))) {
            throw invalid("Pie charts require exactly one y field and no series_field.");
        }
    }

    private void validateData(ChartSpecBo spec, QueryResult result) {
        if (result.truncated())
            throw invalid("Query result is truncated. Aggregate or filter the SQL first.");
        if (result.rows().isEmpty()) throw invalid("Query result is empty.");
        var fields = new HashSet<>(spec.yFields());
        fields.add(spec.xField());
        if (StringUtils.hasText(spec.seriesField())) fields.add(spec.seriesField());
        if (!result.columns().containsAll(fields))
            throw invalid("Chart fields must exist in the query result.");

        for (String field : spec.yFields()) validateNumbers(result, spec, field);
        Set<List<Object>> dimensions = new HashSet<>();
        Set<Object> groups = new HashSet<>();
        for (Map<String, Object> row : result.rows()) {
            Object x =
                    Optional.ofNullable(row.get(spec.xField()))
                            .orElseThrow(
                                    () ->
                                            invalid(
                                                    "x_field contains null. Handle missing"
                                                            + " dimensions in SQL."));
            Object group =
                    StringUtils.hasText(spec.seriesField())
                            ? Optional.ofNullable(row.get(spec.seriesField()))
                                    .orElseThrow(
                                            () ->
                                                    invalid(
                                                            "series_field contains null. Handle it"
                                                                    + " in SQL."))
                            : "";
            groups.add(group);
            if ((long) groups.size() * spec.yFields().size() > MAX_SERIES) {
                throw invalid(
                        "Charts support at most "
                                + MAX_SERIES
                                + " series. Reduce y_fields or filter series_field in SQL.");
            }
            if ("scatter".equals(spec.chartType())) {
                if (!(x instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    throw invalid("Scatter charts require a numeric x_field.");
                }
            } else if (!dimensions.add(List.of(x, group))) {
                throw invalid(
                        "Duplicate chart dimensions. Aggregate by x_field and series_field in"
                                + " SQL.");
            }
        }
    }

    private void validateNumbers(QueryResult result, ChartSpecBo spec, String field) {
        boolean hasUsableValue = false;
        boolean pie = "pie".equals(spec.chartType());
        for (Map<String, Object> row : result.rows()) {
            Optional<Object> value = Optional.ofNullable(row.get(field));
            if (value.isEmpty() && Set.of("bar", "line").contains(spec.chartType())) continue;
            Number number =
                    value.filter(Number.class::isInstance)
                            .map(Number.class::cast)
                            .filter(candidate -> Double.isFinite(candidate.doubleValue()))
                            .orElseThrow(() -> invalid("Chart measure must be numeric: " + field));
            if (pie && number.doubleValue() < 0) {
                throw invalid("Pie charts cannot contain negative values.");
            }
            hasUsableValue |= !pie || number.doubleValue() > 0;
        }
        if (!hasUsableValue)
            throw invalid(
                    pie
                            ? "Pie charts require at least one positive value."
                            : "Chart measure has no numeric values: " + field);
    }

    private BusinessException invalid(String message) {
        return BusinessException.of(ErrorCode.BAD_REQUEST, message);
    }
}

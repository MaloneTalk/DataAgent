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
package io.github.malonetalk.model.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.model.bo.ChartPayloadBo;
import io.github.malonetalk.model.bo.QuerySnapshotBo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ChartConverter {
    private static final int MAX_SNAPSHOT_BYTES = 256 * 1024;
    private final ObjectMapper objectMapper;

    public Optional<Map<String, Object>> snapshot(String sql, QueryResult result)
            throws JsonProcessingException {
        List<Map<String, Object>> rows = result.rows().stream().map(this::jsonRow).toList();
        var snapshot =
                new QuerySnapshotBo(
                        UUID.randomUUID().toString(),
                        sql,
                        new QueryResult(
                                result.columns(), rows, result.totalRows(), result.truncated()));
        Map<String, Object> value = toMetadata(snapshot);
        return objectMapper.writeValueAsBytes(value).length <= MAX_SNAPSHOT_BYTES
                ? Optional.of(value)
                : Optional.empty();
    }

    public Map<String, Object> toMetadata(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() {});
    }

    public Map<String, Object> toMetadata(ChartPayloadBo chart) {
        var spec = chart.spec();
        List<Map<String, Object>> rows =
                chart.rows().stream()
                        .map(
                                row -> {
                                    Map<String, Object> converted = new LinkedHashMap<>(row);
                                    if (!"scatter".equals(spec.chartType())) {
                                        converted.computeIfPresent(
                                                spec.xField(), (field, value) -> value.toString());
                                    }
                                    if (StringUtils.hasText(spec.seriesField())) {
                                        converted.computeIfPresent(
                                                spec.seriesField(),
                                                (field, value) -> value.toString());
                                    }
                                    return converted;
                                })
                        .toList();
        return toMetadata((Object) new ChartPayloadBo(spec, rows));
    }

    private Map<String, Object> jsonRow(Map<String, Object> row) {
        Map<String, Object> converted = new LinkedHashMap<>();
        row.forEach(
                (field, value) ->
                        converted.put(
                                field,
                                value == null
                                                || value instanceof String
                                                || value instanceof Number
                                                || value instanceof Boolean
                                        ? value
                                        : value.toString()));
        return converted;
    }
}

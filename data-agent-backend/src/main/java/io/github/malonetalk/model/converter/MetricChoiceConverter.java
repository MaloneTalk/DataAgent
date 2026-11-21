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

import static io.github.malonetalk.common.SemanticConstants.METRIC_CANDIDATE_DISPLAY_LIMIT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.model.bo.MetricChoiceBo;
import io.github.malonetalk.model.bo.MetricInfoBo;
import io.github.malonetalk.utils.MetricAliasUtils;
import io.github.malonetalk.utils.Sha256Utils;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetricChoiceConverter {
    private final ObjectMapper objectMapper;

    public List<MetricChoiceBo> choices(List<MetricInfoBo> candidates) {
        return candidates.stream().map(this::choice).toList();
    }

    private MetricChoiceBo choice(MetricInfoBo metric) {
        try {
            byte[] definition =
                    objectMapper.writeValueAsBytes(
                            Arrays.asList(
                                    metric.getName(),
                                    metric.getAliases(),
                                    metric.getMeasureExpr(),
                                    metric.getFilters(),
                                    metric.getTimeField(),
                                    metric.getCalculationType(),
                                    metric.getValueType(),
                                    metric.getUnit(),
                                    metric.getResultMultiplier(),
                                    metric.getCurrencyCode(),
                                    metric.getDescription()));
            return new MetricChoiceBo(
                    metric.getId(), metric.getMetricKey(), Sha256Utils.sha256Hex(definition));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot version metric definition", exception);
        }
    }

    public String write(List<MetricChoiceBo> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot save metric candidates", exception);
        }
    }

    public List<MetricChoiceBo> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<MetricChoiceBo>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read metric candidates", exception);
        }
    }

    public Optional<MetricChoiceBo> select(List<MetricChoiceBo> candidates, String reply) {
        String normalized = MetricAliasUtils.normalize(reply);
        // Exact keys take precedence over numeric choices.
        var byKey =
                candidates.stream()
                        .filter(candidate -> candidate.metricKey().equalsIgnoreCase(reply.strip()))
                        .toList();
        if (byKey.size() == 1) return Optional.of(byKey.get(0));
        String choice = normalized.replaceFirst("^(请选择|选择|选)\\s*", "");
        return IntStream.range(0, Math.min(candidates.size(), METRIC_CANDIDATE_DISPLAY_LIMIT))
                .filter(
                        i ->
                                choice.equals(Integer.toString(i + 1))
                                        || choice.equals(Character.toString('a' + i)))
                .mapToObj(candidates::get)
                .findFirst();
    }
}

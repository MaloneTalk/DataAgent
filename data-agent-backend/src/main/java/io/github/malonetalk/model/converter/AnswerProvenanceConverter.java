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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.model.po.AnswerProvenancePo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.DatasourceUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.MetricUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.PythonUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.SqlUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.TableUse;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerProvenanceConverter {

    private final ObjectMapper objectMapper;

    public AnswerProvenancePo toPo(String sessionId, String answerId, AnswerProvenanceVo evidence) {
        AnswerProvenancePo provenance = new AnswerProvenancePo();
        provenance.setAnswerId(answerId);
        provenance.setSessionId(sessionId);
        provenance.setStatus(evidence.status());
        provenance.setDatasourcesJson(write(evidence.datasources()));
        provenance.setSqlExecutionsJson(write(evidence.sqlExecutions()));
        provenance.setMetricReferencesJson(write(evidence.metricReferences()));
        provenance.setPythonExecutionsJson(write(evidence.pythonExecutions()));
        provenance.setTablesJson(write(evidence.tables()));
        provenance.setWarningsJson(write(evidence.warnings()));
        return provenance;
    }

    public Optional<AnswerProvenanceVo> toVo(AnswerProvenancePo provenance) {
        try {
            return Optional.of(
                    AnswerProvenanceVo.builder()
                            .status(provenance.getStatus())
                            .datasources(
                                    readList(provenance.getDatasourcesJson(), DatasourceUse.class))
                            .sqlExecutions(
                                    readList(provenance.getSqlExecutionsJson(), SqlUse.class))
                            .metricReferences(
                                    readList(provenance.getMetricReferencesJson(), MetricUse.class))
                            .pythonExecutions(
                                    readList(provenance.getPythonExecutionsJson(), PythonUse.class))
                            .tables(readList(provenance.getTablesJson(), TableUse.class))
                            .warnings(readList(provenance.getWarningsJson(), String.class))
                            .build());
        } catch (JsonProcessingException e) {
            log.warn(
                    "Skipping invalid answer provenance: answerId={}", provenance.getAnswerId(), e);
            return Optional.empty();
        }
    }

    private <T> List<T> readList(String json, Class<T> elementType) throws JsonProcessingException {
        return objectMapper.readValue(
                json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot save answer provenance", e);
        }
    }
}

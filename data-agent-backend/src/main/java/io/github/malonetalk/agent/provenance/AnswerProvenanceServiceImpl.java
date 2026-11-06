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
package io.github.malonetalk.agent.provenance;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.mapper.AnswerProvenanceMapper;
import io.github.malonetalk.mapper.UserSessionMapper;
import io.github.malonetalk.model.dto.DataEvidencePageQuery;
import io.github.malonetalk.model.po.AnswerProvenancePo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.DataEvidenceVo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.DatasourceUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.MetricUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.PythonUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.SqlUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.TableUse;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerProvenanceServiceImpl implements AnswerProvenanceService {

    private final AnswerProvenanceMapper provenanceMapper;
    private final ObjectMapper objectMapper;
    private final AnswerProvenanceBuilder evidenceBuilder;
    private final UserSessionMapper userSessionMapper;

    @Override
    public void deleteSession(String sessionId) {
        provenanceMapper.deleteBySessionId(sessionId);
    }

    @Override
    public PageResponse<DataEvidenceVo> getPage(DataEvidencePageQuery query, Integer userId) {
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        String sessionId = SemanticUtils.trimToNull(query.sessionId());
        List<String> sessionIds =
                userId == null ? null : userSessionMapper.selectSessionIdsByUserId(userId);
        if (sessionIds != null
                && (sessionIds.isEmpty() || sessionId != null && !sessionIds.contains(sessionId))) {
            return PageResponse.empty(pageNumber, pageSize);
        }

        IPage<AnswerProvenancePo> page =
                provenanceMapper.selectPage(
                        new Page<>(pageNumber, pageSize),
                        sessionId,
                        SemanticUtils.trimToNull(query.answerId()),
                        sessionId == null ? sessionIds : null);
        List<DataEvidenceVo> evidence =
                page.getRecords().stream()
                        .flatMap(
                                stored ->
                                        read(stored)
                                                .map(
                                                        value ->
                                                                new DataEvidenceVo(
                                                                        stored.getAnswerId(),
                                                                        stored.getSessionId(),
                                                                        value))
                                                .stream())
                        .toList();
        return PageResponse.of(evidence, page.getTotal(), pageNumber, pageSize);
    }

    @Override
    @Transactional
    public void save(
            String sessionId, String answerId, List<ChatStreamEvent> events, String status) {
        var stored =
                Optional.ofNullable(
                        provenanceMapper.selectForUpdateBySessionIdAndAnswerId(
                                sessionId, answerId));
        evidenceBuilder
                .build(events, status, stored.flatMap(this::read))
                .ifPresent(evidence -> persist(sessionId, answerId, evidence, stored.isPresent()));
    }

    private void persist(
            String sessionId, String answerId, AnswerProvenanceVo evidence, boolean exists) {
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
        if (exists) {
            provenanceMapper.updateBySessionIdAndAnswerId(provenance);
        } else {
            provenanceMapper.insert(provenance);
        }
    }

    private Optional<AnswerProvenanceVo> read(AnswerProvenancePo provenance) {
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

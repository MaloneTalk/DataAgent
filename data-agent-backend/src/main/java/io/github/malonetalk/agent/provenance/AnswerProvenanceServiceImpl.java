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
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.mapper.AnswerProvenanceMapper;
import io.github.malonetalk.mapper.UserSessionMapper;
import io.github.malonetalk.model.converter.AnswerProvenanceConverter;
import io.github.malonetalk.model.dto.DataEvidencePageQuery;
import io.github.malonetalk.model.po.AnswerProvenancePo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.DataEvidenceVo;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnswerProvenanceServiceImpl implements AnswerProvenanceService {

    private final AnswerProvenanceMapper provenanceMapper;
    private final AnswerProvenanceConverter provenanceConverter;
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
                                        provenanceConverter
                                                .toVo(stored)
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
                .build(events, status, stored.flatMap(provenanceConverter::toVo))
                .ifPresent(evidence -> persist(sessionId, answerId, evidence, stored.isPresent()));
    }

    private void persist(
            String sessionId, String answerId, AnswerProvenanceVo evidence, boolean exists) {
        AnswerProvenancePo provenance = provenanceConverter.toPo(sessionId, answerId, evidence);
        if (exists) {
            provenanceMapper.updateBySessionIdAndAnswerId(provenance);
        } else {
            provenanceMapper.insert(provenance);
        }
    }
}

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

import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.model.vo.AnswerProvenanceVo;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AnswerProvenanceService {

    Optional<AnswerProvenanceVo> find(String sessionId, String answerId);

    Map<String, AnswerProvenanceVo> listBySession(String sessionId);

    void deleteSession(String sessionId);

    void save(String sessionId, String answerId, List<ChatStreamEvent> events, String status);
}

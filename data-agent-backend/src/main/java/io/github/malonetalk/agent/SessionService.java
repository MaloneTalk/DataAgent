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
package io.github.malonetalk.agent;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.github.malonetalk.agent.provenance.AnswerProvenanceService;
import io.github.malonetalk.dto.SessionDatasourceBinding;
import io.github.malonetalk.dto.SessionInfo;
import io.github.malonetalk.dto.TurnItem;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.UserSessionMapper;
import io.github.malonetalk.model.converter.SessionHistoryConverter;
import io.github.malonetalk.service.MetricClarificationService;
import io.github.malonetalk.service.SessionDatasourceService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final javax.sql.DataSource dataSource;
    @Getter private final AgentStateStore stateStore;
    private final SessionDatasourceService sessionDatasourceService;
    private final UserSessionMapper userSessionMapper;
    private final MetricClarificationService metricClarification;
    private final AnswerProvenanceService answerProvenanceService;
    private final SessionHistoryConverter sessionHistoryConverter;

    public String createAgentScopeSession(int userId) {
        return createSession(userId, SessionIdFactory.AGENT_SCOPE, null);
    }

    /** 创建会话：按 Agent 类型生成前缀化 ID，并登记用户归属。 */
    public String createSession(int userId, String agentCode, String externalSessionId) {
        String base = SessionIdFactory.build(agentCode, userId, externalSessionId);
        String sessionId = base;
        for (int i = 0; i < 5; i++) {
            if (userSessionMapper.insertIgnore(userId, sessionId) > 0) {
                return sessionId;
            }
            sessionId = base + "_" + System.currentTimeMillis() + "_" + collisionToken();
        }
        throw new IllegalStateException("Failed to allocate unique session id: " + base);
    }

    private static String collisionToken() {
        return Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000));
    }

    public void requireOwnership(Integer userId, String sessionId) {
        if (userId != null && !userSessionMapper.exists(userId, sessionId)) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    public List<Msg> getSessionDebug(String sessionId, Integer userId) {
        requireOwnership(userId, sessionId);
        return loadMessages(sessionId);
    }

    public List<TurnItem> getSessionHistory(String sessionId, Integer userId) {
        requireOwnership(userId, sessionId);
        return sessionHistoryConverter.convert(loadMessages(sessionId));
    }

    private List<Msg> loadMessages(String sessionId) {
        String owner = ownerId(sessionId);
        return stateStore
                .get(owner, sessionId, "agent_state", AgentState.class)
                .map(AgentState::getContext)
                .orElseGet(
                        () -> stateStore.getList(owner, sessionId, "memory_messages", Msg.class));
    }

    private String ownerId(String sessionId) {
        return Optional.ofNullable(userSessionMapper.selectUserIdBySessionId(sessionId))
                .map(String::valueOf)
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public Optional<String> getLatestUserMessageId(String sessionId) {
        List<Msg> messages = loadMessages(sessionId);
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == MsgRole.USER)
                return Optional.ofNullable(messages.get(i).getId());
        }
        return Optional.empty();
    }

    @Transactional
    public void clearSession(String sessionId, Integer userId) {
        requireOwnership(userId, sessionId);
        doClearSession(sessionId);
    }

    private void doClearSession(String sessionId) {
        metricClarification.clearSession(sessionId);
        stateStore.delete(ownerId(sessionId), sessionId);
        answerProvenanceService.deleteSession(sessionId);
        sessionDatasourceService.deleteBySessionId(sessionId);
        userSessionMapper.deleteBySessionId(sessionId);
    }

    /** 清空全部会话；调用入口要求 analysis:session:delete-all。 */
    @Transactional
    public void clearAllSessions() {
        userSessionMapper.selectAllSessionIds().stream()
                .filter(id -> SessionIdFactory.isAgent(id, SessionIdFactory.AGENT_SCOPE))
                .forEach(this::doClearSession);
    }

    /**
     * 列出会话列表。
     *
     * @param userId null 表示 admin（全量），非 null 表示只查该用户的会话
     */
    public List<SessionInfo> listSessions(Integer userId) {
        List<String> visibleIds =
                userId == null
                        ? userSessionMapper.selectAllSessionIds()
                        : userSessionMapper.selectSessionIdsByUserId(userId);
        if (visibleIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, String[]> timestamps = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "SELECT us.session_id, MIN(s.created_at), MAX(s.updated_at) FROM"
                                    + " agentscope_states s JOIN user_session us ON s.session_id ="
                                    + " CONCAT(us.user_id, ':', us.session_id) GROUP BY"
                                    + " us.session_id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                timestamps.put(rs.getString(1), new String[] {rs.getString(2), rs.getString(3)});
            }
        } catch (Exception e) {
            log.error("Error listing session timestamps", e);
        }

        Map<String, List<SessionInfo.Datasource>> bindings = new HashMap<>();
        for (SessionDatasourceBinding row :
                sessionDatasourceService.listBindingsWithDatasourceName()) {
            bindings.computeIfAbsent(row.getSessionId(), key -> new ArrayList<>())
                    .add(
                            new SessionInfo.Datasource(
                                    row.getDatasourceId(), row.getDatasourceName()));
        }

        List<SessionInfo> result = new ArrayList<>();
        for (String sid : visibleIds) {
            if (!SessionIdFactory.isAgent(sid, SessionIdFactory.AGENT_SCOPE)) {
                continue;
            }
            List<Msg> messages = loadMessages(sid);
            if (messages.isEmpty()) continue;

            result.add(
                    sessionHistoryConverter.toInfo(
                            sid,
                            messages,
                            timestamps.getOrDefault(sid, new String[] {"", ""}),
                            bindings.getOrDefault(sid, List.of())));
        }

        result.sort((a, b) -> b.lastActiveAt().compareTo(a.lastActiveAt()));
        return result;
    }
}

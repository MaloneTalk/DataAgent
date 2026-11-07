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
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.mysql.MysqlSession;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.github.malonetalk.agent.provenance.AnswerProvenanceService;
import io.github.malonetalk.dto.SessionDatasourceBinding;
import io.github.malonetalk.dto.SessionInfo;
import io.github.malonetalk.dto.TurnItem;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.SessionDatasourceMapper;
import io.github.malonetalk.mapper.UserSessionMapper;
import io.github.malonetalk.model.converter.SessionHistoryConverter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
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
    private final SessionDatasourceMapper sessionDatasourceMapper;
    private final UserSessionMapper userSessionMapper;
    private final AnswerProvenanceService answerProvenanceService;
    private final SessionHistoryConverter sessionHistoryConverter;

    @Getter(lazy = true)
    private final MysqlSession session =
            new MysqlSession(dataSource, currentDatabaseName(), "agentscope_sessions", false);

    public boolean hasDatasourceBinding(String sessionId) {
        return sessionDatasourceMapper.selectBySessionId(sessionId) != null;
    }

    private String currentDatabaseName() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getCatalog();
        } catch (SQLException e) {
            throw new IllegalStateException("无法获取当前数据库名", e);
        }
    }

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
        Session session = getSession();
        if (!session.exists(SimpleSessionKey.of(sessionId))) {
            return Collections.emptyList();
        }
        return session.getList(SimpleSessionKey.of(sessionId), "memory_messages", Msg.class);
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
        getSession().delete(SimpleSessionKey.of(sessionId));
        answerProvenanceService.deleteSession(sessionId);
        sessionDatasourceMapper.deleteBySessionId(sessionId);
        userSessionMapper.deleteBySessionId(sessionId);
    }

    /** 清空全部会话；调用入口要求 analysis:session:delete-all。 */
    @Transactional
    public void clearAllSessions() {
        MysqlSession session = getSession();
        Set<SessionKey> keys = session.listSessionKeys();
        if (keys != null) {
            for (SessionKey key : keys) {
                doClearSession(key.toIdentifier());
            }
        }
    }

    /**
     * 列出会话列表。
     *
     * @param userId null 表示 admin（全量），非 null 表示只查该用户的会话
     */
    public List<SessionInfo> listSessions(Integer userId) {
        MysqlSession session = getSession();
        Set<SessionKey> keys = session.listSessionKeys();

        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> visibleIds;
        if (userId == null) {
            visibleIds = keys.stream().map(SessionKey::toIdentifier).collect(Collectors.toSet());
        } else {
            visibleIds = new HashSet<>(userSessionMapper.selectSessionIdsByUserId(userId));
        }

        Map<String, String[]> timestamps = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "SELECT session_id, MIN(created_at), MAX(updated_at)"
                                        + " FROM agentscope_sessions GROUP BY session_id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                timestamps.put(rs.getString(1), new String[] {rs.getString(2), rs.getString(3)});
            }
        } catch (Exception e) {
            log.error("Error listing session timestamps", e);
        }

        Map<String, String[]> bindings = new HashMap<>();
        for (SessionDatasourceBinding row :
                sessionDatasourceMapper.listBindingsWithDatasourceName()) {
            bindings.put(
                    row.getSessionId(),
                    new String[] {
                        row.getDatasourceId() != null
                                ? String.valueOf(row.getDatasourceId())
                                : null,
                        row.getDatasourceName()
                    });
        }

        List<SessionInfo> result = new ArrayList<>();
        for (SessionKey key : keys) {
            String sid = key.toIdentifier();
            if (!SessionIdFactory.isAgent(sid, SessionIdFactory.AGENT_SCOPE)
                    || !visibleIds.contains(sid)) {
                continue;
            }
            List<Msg> messages = session.getList(key, "memory_messages", Msg.class);

            String title = "";
            if (messages != null) {
                for (Msg msg : messages) {
                    if (msg.getRole() == MsgRole.USER) {
                        title = getTextContent(msg);
                        if (title.length() > 30) {
                            title = title.substring(0, 30);
                        }
                        break;
                    }
                }
            }
            if (title.isEmpty()) {
                title = sid.length() > 20 ? sid.substring(0, 20) : sid;
            }

            String[] times = timestamps.getOrDefault(sid, new String[] {"", ""});
            String[] binding = bindings.get(sid);
            Integer datasourceId =
                    binding == null || binding[0] == null ? null : Integer.valueOf(binding[0]);
            String datasourceName = binding == null ? null : binding[1];
            result.add(
                    new SessionInfo(sid, title, times[0], times[1], datasourceId, datasourceName));
        }

        result.sort((a, b) -> b.lastActiveAt().compareTo(a.lastActiveAt()));
        return result;
    }

    /**
     * Extract text content from a message. Concatenates text from all
     * text-containing blocks (TextBlock and ThinkingBlock).
     */
    private static String getTextContent(Msg msg) {
        String thinking =
                msg.getContent().stream()
                        .filter(block -> block instanceof ThinkingBlock)
                        .map(block -> ((ThinkingBlock) block).getThinking())
                        .collect(Collectors.joining("\n"));

        String text =
                msg.getContent().stream()
                        .filter(block -> block instanceof TextBlock)
                        .map(block -> ((TextBlock) block).getText())
                        .collect(Collectors.joining("\n"));

        if (!thinking.isEmpty() && !text.isEmpty()) {
            return thinking + "\n\n" + text;
        } else if (!thinking.isEmpty()) {
            return thinking;
        } else if (!text.isEmpty()) {
            return text;
        } else {
            return "[No response]";
        }
    }
}

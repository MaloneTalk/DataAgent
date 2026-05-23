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

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.mysql.MysqlSession;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.SessionInfo;
import io.github.malonetalk.dto.TurnItem;
import io.github.malonetalk.enums.ChatStreamEventType;
import io.github.malonetalk.utils.MsgUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SessionService {

    private final DataSource dataSource;
    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();

    public SessionService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Session getOrCreateSession(String sessionId) {
        return sessionCache.computeIfAbsent(
                sessionId,
                k -> new MysqlSession(dataSource, "data_agent", "agentscope_sessions", false));
    }

    public List<Msg> getSessionDebug(String sessionId) {
        Session session = getOrCreateSession(sessionId);
        if (!session.exists(SimpleSessionKey.of(sessionId))) {
            return Collections.emptyList();
        }
        return session.getList(SimpleSessionKey.of(sessionId), "memory_messages", Msg.class);
    }

    public List<TurnItem> getSessionHistory(String sessionId) {
        Session session = getOrCreateSession(sessionId);

        if (!session.exists(SimpleSessionKey.of(sessionId))) {
            return Collections.emptyList();
        }

        List<Msg> messages =
                session.getList(SimpleSessionKey.of(sessionId), "memory_messages", Msg.class);

        List<TurnItem> turns = new ArrayList<>();
        List<ContentBlock> agentBlocks = null;

        for (Msg msg : messages) {
            boolean isUser = msg.getRole() == MsgRole.USER;
            if (isUser) {
                if (agentBlocks != null) {
                    turns.add(buildAgentTurn(agentBlocks));
                    agentBlocks = null;
                }
                String text =
                        msg.getContent().stream()
                                .filter(block -> block instanceof TextBlock)
                                .map(block -> ((TextBlock) block).getText())
                                .filter(t -> t != null && !t.isEmpty())
                                .collect(Collectors.joining());
                turns.add(new TurnItem(MsgRole.USER.name(), text, List.of()));
            } else {
                if (agentBlocks == null) {
                    agentBlocks = new ArrayList<>();
                }
                agentBlocks.addAll(msg.getContent());
            }
        }
        if (agentBlocks != null) {
            turns.add(buildAgentTurn(agentBlocks));
        }

        return turns;
    }

    private TurnItem buildAgentTurn(List<ContentBlock> blocks) {
        StringBuilder content = new StringBuilder();
        List<ChatStreamEvent> traceSteps = new ArrayList<>();

        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock tb) {
                appendText(content, tb);
            } else if (block instanceof ThinkingBlock tb) {
                appendThinking(traceSteps, tb);
            } else if (block instanceof ToolUseBlock tub) {
                appendToolCall(traceSteps, tub);
            } else if (block instanceof ToolResultBlock trb) {
                appendToolResult(traceSteps, trb);
            }
        }

        return new TurnItem(MsgRole.ASSISTANT.name(), content.toString(), traceSteps);
    }

    private void appendText(StringBuilder content, TextBlock tb) {
        String text = tb.getText();
        if (text != null && !text.isEmpty()) {
            content.append(text);
        }
    }

    private void appendThinking(List<ChatStreamEvent> traceSteps, ThinkingBlock tb) {
        String thinking = tb.getThinking();
        if (thinking == null || thinking.isEmpty()) {
            return;
        }
        int lastIdx = traceSteps.size() - 1;
        if (lastIdx >= 0 && traceSteps.get(lastIdx).type() == ChatStreamEventType.THINKING) {
            String merged =
                    (traceSteps.get(lastIdx).content() != null
                                    ? traceSteps.get(lastIdx).content()
                                    : "")
                            + thinking;
            traceSteps.set(lastIdx, thinkingEvent(merged));
        } else {
            traceSteps.add(thinkingEvent(thinking));
        }
    }

    private void appendToolCall(List<ChatStreamEvent> traceSteps, ToolUseBlock tub) {
        traceSteps.add(
                new ChatStreamEvent(
                        ChatStreamEventType.TOOL_CALL,
                        null,
                        false,
                        null,
                        new ChatStreamEvent.ToolCallInfo(
                                tub.getId(), tub.getName(), tub.getInput()),
                        null));
    }

    private void appendToolResult(List<ChatStreamEvent> traceSteps, ToolResultBlock trb) {
        String outputText =
                trb.getOutput().stream()
                        .filter(b -> b instanceof TextBlock)
                        .map(b -> ((TextBlock) b).getText())
                        .filter(t -> t != null && !t.isEmpty())
                        .collect(Collectors.joining("\n"));
        traceSteps.add(
                new ChatStreamEvent(
                        ChatStreamEventType.TOOL_RESULT,
                        null,
                        false,
                        null,
                        null,
                        new ChatStreamEvent.ToolResultInfo(
                                trb.getId(), trb.getName(), outputText)));
    }

    private static ChatStreamEvent thinkingEvent(String thinking) {
        return new ChatStreamEvent(ChatStreamEventType.THINKING, null, false, thinking, null, null);
    }

    public void clearSession(String sessionId) {
        Session session = sessionCache.remove(sessionId);
        if (session != null) {
            session.delete(SimpleSessionKey.of(sessionId));
        }
    }

    public void clearAllSessions() {
        sessionCache.clear();
    }

    public List<SessionInfo> listSessions() {
        MysqlSession session =
                new MysqlSession(dataSource, "data_agent", "agentscope_sessions", false);
        Set<SessionKey> keys = session.listSessionKeys();

        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
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

        List<SessionInfo> result = new ArrayList<>();
        for (SessionKey key : keys) {
            String sid = key.toIdentifier();
            List<Msg> messages = session.getList(key, "memory_messages", Msg.class);

            String title = "";
            if (messages != null) {
                for (Msg msg : messages) {
                    if (msg.getRole() == MsgRole.USER) {
                        title = MsgUtils.getTextContent(msg);
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
            result.add(new SessionInfo(sid, title, times[0], times[1]));
        }

        result.sort((a, b) -> b.lastActiveAt().compareTo(a.lastActiveAt()));
        return result;
    }
}

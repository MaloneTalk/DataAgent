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

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.mysql.MysqlSession;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.tool.Toolkit;
import io.github.malonetalk.agent.tools.MarkAgentTool;
import io.github.malonetalk.convertor.EventConverter;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.utils.MsgUtils;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
public class AgentService {

    private static final String SESSION_DATABASE_NAME = "data_agent";
    private static final String SESSION_TABLE_NAME = "agentscope_sessions";
    private static final String SESSION_KEY_PREFIX = "data-agent:";

    private final ModelFactory modelFactory;
    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();
    private final List<MarkAgentTool> allToolBeans;
    private final DataSource dataSource;
    private Toolkit toolkit;

    public AgentService(
            ModelFactory modelFactory, List<MarkAgentTool> allToolBeans, DataSource dataSource) {
        this.modelFactory = modelFactory;
        this.allToolBeans = allToolBeans;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        this.toolkit = new Toolkit();
        allToolBeans.forEach(this.toolkit::registerTool);
    }

    private Session getOrCreateSession(String sessionId) {
        return sessionCache.computeIfAbsent(sessionId, key -> createPersistentSessionStore());
    }

    MysqlSession createPersistentSessionStore() {
        return new MysqlSession(dataSource, SESSION_DATABASE_NAME, SESSION_TABLE_NAME, true);
    }

    public String chat(String sessionId, String userInput) {
        ReActAgent agent = createAgent();

        MysqlSession session = (MysqlSession) getOrCreateSession(sessionId);
        agent.loadIfExists(session, buildNamespacedSessionId(sessionId));

        Msg userMsg = Msg.builder().textContent(userInput).build();
        Msg response = agent.call(userMsg).block();

        agent.saveTo(session, buildNamespacedSessionId(sessionId));
        return MsgUtils.getTextContent(response);
    }

    public Flux<ChatStreamEvent> chatStream(String sessionId, String userInput) {
        ReActAgent agent = createAgent();

        MysqlSession session = (MysqlSession) getOrCreateSession(sessionId);
        agent.loadIfExists(session, buildNamespacedSessionId(sessionId));

        Msg userMsg = Msg.builder().textContent(userInput).build();

        StreamOptions streamOptions =
                StreamOptions.builder()
                        .eventTypes(EventType.REASONING, EventType.TOOL_RESULT, EventType.SUMMARY)
                        .incremental(true)
                        .includeReasoningResult(true)
                        .build();

        return agent.stream(userMsg, streamOptions)
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(signalType -> agent.saveTo(session, buildNamespacedSessionId(sessionId)))
                .flatMapIterable(EventConverter::map);
    }

    private ReActAgent createAgent() {
        return ReActAgent.builder()
                .name("DataAgent")
                .sysPrompt(
                        """
                        你是一个数据分析助手，负责通过工具查询数据库并回答用户问题。

                        工作流程：
                        1. 先调用 get_tables，获取当前可见表的结构化列表。每个表已附带它与其他表的 relations（关系），不要再到其他工具找关系。
                        2. get_tables 返回分页数据时，必须检查 data.hasNext、data.totalPages 和 data.items。
                           如果当前页不足以覆盖候选表，必须继续翻页，直到拿到足够的表与关系信息，再决定下一步。
                        3. 根据用户问题选择相关表，再调用 get_table_schema 获取目标表的列结构（columns）。
                        4. get_table_schema 中的 columns 是分页数据。
                           必须检查 columns.hasNext、columns.totalPages，必要时继续翻页，直到拿到足够的列信息。
                        5. 基于 get_tables 中的 relations 与 get_table_schema 中的 columns 生成合适的 SELECT SQL。
                        6. 调用 execute_sql 执行 SQL。
                        7. 根据查询结果回答用户问题。

                        工具返回协议：
                        1. 所有工具都返回 success/data/error 三段结构。
                        2. 必须先检查 success。
                        3. 只有 success=true 时，才能使用 data 中的内容继续推理。
                        4. 如果 success=false，必须读取 error.code 和 error.message，先修正问题、换表、重试或向用户解释原因，不能把 error 当成正常数据继续生成 SQL。
                        5. 如果 data 是分页结构，必须优先检查 items、hasNext、totalPages、page、pageSize，不能把单页数据误当成全量数据。
                        6. 当分页数据不足以支撑结论时，必须显式继续翻页，不能基于不完整 schema 猜测表、列或关系。

                        SQL 约束：
                        1. 只允许 SELECT。
                        2. 生成 SQL 之前，必须先查看相关表结构，确认表名、列名、类型与可见范围。
                        3. 如果工具返回失败，不要跳过失败直接猜测表结构或继续执行 SQL。
                        """)
                .model(modelFactory.createModel())
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .maxIters(16)
                .build();
    }

    public void clearSession(String sessionId) {
        closeSessionStore(sessionCache.remove(sessionId));
        MysqlSession sessionStore = createPersistentSessionStore();
        try {
            sessionStore.delete(namespacedSessionKey(sessionId));
        } finally {
            sessionStore.close();
        }
    }

    public void clearAllSessions() {
        sessionCache.values().forEach(this::closeSessionStore);
        sessionCache.clear();

        MysqlSession sessionStore = createPersistentSessionStore();
        try {
            Set<SessionKey> sessionKeys = sessionStore.listSessionKeys();
            for (SessionKey sessionKey : sessionKeys) {
                if (isManagedSessionKey(sessionKey)) {
                    sessionStore.delete(sessionKey);
                }
            }
        } finally {
            sessionStore.close();
        }
    }

    private SimpleSessionKey namespacedSessionKey(String sessionId) {
        return SimpleSessionKey.of(buildNamespacedSessionId(sessionId));
    }

    private String buildNamespacedSessionId(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private boolean isManagedSessionKey(SessionKey sessionKey) {
        return sessionKey.toIdentifier().startsWith(SESSION_KEY_PREFIX);
    }

    private void closeSessionStore(Session session) {
        if (session instanceof MysqlSession mysqlSession) {
            mysqlSession.close();
        }
    }
}

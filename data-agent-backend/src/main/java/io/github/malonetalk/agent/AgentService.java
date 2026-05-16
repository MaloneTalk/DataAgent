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
import io.agentscope.core.tool.Toolkit;
import io.github.malonetalk.agent.tools.ExecuteSqlTool;
import io.github.malonetalk.agent.tools.GetTableSchemaTool;
import io.github.malonetalk.agent.tools.GetTablesTool;
import io.github.malonetalk.convertor.EventConverter;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.utils.MsgUtils;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
public class AgentService {

    private final ModelFactory modelFactory;
    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();
    private Toolkit toolkit;

    private final GetTablesTool getTablesTool;
    private final GetTableSchemaTool getTableSchemaTool;
    private final ExecuteSqlTool executeSqlTool;

    private final DataSource dataSource;

    public AgentService(
            ModelFactory modelFactory,
            GetTablesTool getTablesTool,
            GetTableSchemaTool getTableSchemaTool,
            ExecuteSqlTool executeSqlTool,
            DataSource dataSource) {
        this.modelFactory = modelFactory;
        this.getTablesTool = getTablesTool;
        this.getTableSchemaTool = getTableSchemaTool;
        this.executeSqlTool = executeSqlTool;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        this.toolkit = new Toolkit();
        this.toolkit.registerTool(getTablesTool);
        this.toolkit.registerTool(getTableSchemaTool);
        this.toolkit.registerTool(executeSqlTool);
    }

    private Session getOrCreateSession(String sessionId) {
        return sessionCache.computeIfAbsent(
                sessionId,
                k -> new MysqlSession(dataSource, "data_agent", "agentscope_sessions", false));
    }

    public String chat(String sessionId, String userInput) {
        ReActAgent agent = createAgent();

        Session session = getOrCreateSession(sessionId);
        agent.loadIfExists(session, sessionId);

        Msg userMsg = Msg.builder().textContent(userInput).build();

        Msg response = agent.call(userMsg).block();

        agent.saveTo(session, sessionId);

        return MsgUtils.getTextContent(response);
    }

    public Flux<ChatStreamEvent> chatStream(String sessionId, String userInput) {
        ReActAgent agent = createAgent();

        Session session = getOrCreateSession(sessionId);
        agent.loadIfExists(session, sessionId);

        Msg userMsg = Msg.builder().textContent(userInput).build();

        StreamOptions streamOptions =
                StreamOptions.builder()
                        .eventTypes(EventType.REASONING, EventType.TOOL_RESULT, EventType.SUMMARY)
                        .incremental(true)
                        .includeReasoningResult(true)
                        .build();

        return agent.stream(userMsg, streamOptions)
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(signalType -> agent.saveTo(session, sessionId))
                .flatMapIterable(EventConverter::map);
    }

    private ReActAgent createAgent() {
        return ReActAgent.builder()
                .name("DataAgent")
                .sysPrompt(
                        """
                        你是一个数据助手，可以帮助用户查询数据库中的数据。请按以下步骤操作：
                        1. 使用 get_tables 工具获取可用的数据库表信息
                        2. 根据用户问题，选择相关的表，使用 get_table_schema 工具获取表结构（列名、类型、主键等）
                        3. 根据表结构信息，生成合适的 SELECT SQL 语句
                        4. 使用 execute_sql 工具执行 SQL 查询
                        5. 根据查询结果回答用户问题
                        注意：仅支持 SELECT 查询，不支持修改操作。生成SQL时请务必先查看表结构，确保列名和类型正确。
                        """)
                .model(modelFactory.createModel())
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .maxIters(10)
                .build();
    }

    public void clearSession(String sessionId) {
        Session session = sessionCache.remove(sessionId);
        if (session != null) {
            session.delete(io.agentscope.core.state.SimpleSessionKey.of(sessionId));
        }
    }

    public void clearAllSessions() {
        sessionCache.clear();
    }
}

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

    public Flux<String> chatStream(String sessionId, String userInput) {
        ReActAgent agent = createAgent();

        Session session = getOrCreateSession(sessionId);
        agent.loadIfExists(session, sessionId);

        Msg userMsg = Msg.builder().textContent(userInput).build();

        // TODO: 区分不同消息块类型，方便前端按类型渲染
        StreamOptions streamOptions =
                StreamOptions.builder()
                        .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                        .incremental(true)
                        .includeReasoningResult(false)
                        .build();

        return agent.stream(userMsg, streamOptions)
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(signalType -> agent.saveTo(session, sessionId))
                .map(event -> MsgUtils.getTextContent(event.getMessage()))
                .filter(text -> text != null && !text.isEmpty());
    }

    private ReActAgent createAgent() {
        return ReActAgent.builder()
                .name("DataAgent")
                .sysPrompt(
                        """
                        你是一个数据分析助手，负责通过工具查询数据库并回答用户问题。

                        工作流程：
                        1. 先调用 get_tables，获取当前可见表的结构化列表。
                        2. 根据用户问题选择相关表，再调用 get_table_schema 获取目标表的结构化表元信息。
                        3. 基于表结构生成合适的 SELECT SQL。
                        4. 调用 execute_sql 执行 SQL。
                        5. 根据查询结果回答用户问题。

                        工具返回协议：
                        1. 所有工具都返回 success/data/error 三段结构。
                        2. 必须先检查 success。
                        3. 只有 success=true 时，才能使用 data 中的内容继续推理。
                        4. 如果 success=false，必须读取 error.code 和 error.message，先修正问题、换表、重试或向用户解释原因，不能把 error 当成正常数据继续生成 SQL。

                        SQL 约束：
                        1. 只允许 SELECT。
                        2. 生成 SQL 之前，必须先查看相关表结构，确认表名、列名、类型与可见范围。
                        3. 如果工具返回失败，不要跳过失败直接猜测表结构或继续执行 SQL。
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

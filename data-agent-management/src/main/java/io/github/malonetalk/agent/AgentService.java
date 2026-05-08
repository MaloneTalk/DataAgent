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
import io.github.malonetalk.agent.tools.GetTablesTool;
import io.github.malonetalk.utils.MsgUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentService {

    private final ModelFactory modelFactory;
    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();
    private Toolkit toolkit;

    private final GetTablesTool getTablesTool;
    private final ExecuteSqlTool executeSqlTool;

    private final DataSource dataSource;

    public AgentService(
            ModelFactory modelFactory,
            GetTablesTool getTablesTool,
            ExecuteSqlTool executeSqlTool,
            DataSource dataSource) {
        this.modelFactory = modelFactory;
        this.getTablesTool = getTablesTool;
        this.executeSqlTool = executeSqlTool;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        this.toolkit = new Toolkit();
        this.toolkit.registerTool(getTablesTool);
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

        // TODO 各种信息块的类型区分，以返回给前端方便按不同的UI渲染
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
                .sysPrompt("""
                        You are a data assistant that helps users query data from databases. Please follow these steps:
                        1. Use the get_tables tool to get available database table information
                        2. Analyze which tables need to be queried based on user questions and generate appropriate SELECT SQL statements
                        3. Use the execute_sql tool to execute the SQL query
                        4. Answer the user question based on the query results
                        Note: Only SELECT queries are supported, modification operations are not allowed.
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

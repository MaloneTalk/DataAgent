package com.malone.agent;

import com.malone.agent.tools.GetTablesTool;
import com.malone.utils.MsgUtils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.mysql.MysqlSession;
import io.agentscope.core.tool.Toolkit;
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

    private  final  DataSource dataSource;
    public AgentService(ModelFactory modelFactory, GetTablesTool getTablesTool, DataSource dataSource) {
        this.modelFactory = modelFactory;
        this.getTablesTool = getTablesTool;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        this.toolkit = new Toolkit();
        this.toolkit.registerTool(getTablesTool);
    }

    private Session getOrCreateSession(String sessionId) {
        return sessionCache.computeIfAbsent(sessionId, k -> new MysqlSession(dataSource,"data_agent","agentscope_sessions",false));
    }

    public String chat(String sessionId, String userInput) {
        ReActAgent agent = createAgent();

        Session session = getOrCreateSession(sessionId);
        agent.loadIfExists(session, sessionId);

        Msg userMsg = Msg.builder()
                .textContent(userInput)
                .build();

        Msg response = agent.call(userMsg).block();

        agent.saveTo(session, sessionId);

        return MsgUtils.getTextContent(response);
    }

    public Flux<String> chatStream(String sessionId, String userInput) {
        ReActAgent agent = createAgent();

        Session session = getOrCreateSession(sessionId);
        agent.loadIfExists(session, sessionId);

        Msg userMsg = Msg.builder()
                .textContent(userInput)
                .build();

        StreamOptions streamOptions = StreamOptions.builder()
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
                .sysPrompt("你是一个数据助手，可以帮助用户查询数据库表信息。请使用提供的工具来获取数据。")
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

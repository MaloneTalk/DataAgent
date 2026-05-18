# DataAgent 多数据源路由 + 结构化 SSE 事件流实现方案

生成时间：2026-05-17  
调研来源：DB-GPT / Chat2DB 多数据源架构、Spring WebFlux ServerSentEvent 文档、AgentScope Java StreamOptions、Reactor Context 传播最佳实践

---

## 一、重要前提：项目是 Spring MVC，非 WebFlux

`pom.xml` 只有 `spring-boot-starter-web`（Servlet 栈），没有 `spring-boot-starter-webflux`。Spring MVC 同样支持 `Flux<ServerSentEvent<T>>` 作为响应类型（依赖 Spring 对 Reactor Publisher 的兼容层），但 Reactor Context 传播和 ThreadLocal 的陷阱必须特别注意。

**`AgentService.chatStream()` 中 `subscribeOn(Schedulers.boundedElastic())` 会将执行切换到 Reactor 线程池，HTTP 请求的 Servlet 线程设置的 ThreadLocal 不会被继承。** 这是 ThreadLocal 方案（方案A）在此场景失效的根本原因。

---

## 二、多数据源显式路由方案

### 2.1 现状问题

`ActiveDatasourceSupport` 当前逻辑：`findByStatus(ACTIVE)`，多于一个 active 数据源时直接抛异常。三个工具均为 Spring Bean 单例，均依赖此方法：
- `ExecuteSqlTool`
- `GetTablesTool`（通过 `TableSemanticServiceImpl` 内部调用）
- `GetTableSchemaTool`（通过 `TableSemanticServiceImpl` 内部调用）

### 2.2 三方案对比

| 方案 | 描述 | Reactor 下是否安全 | 改动范围 |
|---|---|---|---|
| **A** | ThreadLocal 传 datasourceId | **不安全**（`subscribeOn` 切线程后 ThreadLocal 丢失） | 小，但有隐蔽 bug |
| **B** | 每次请求动态创建含指定 datasource 的 Toolkit | **安全** | 中（工具去 `@Component`） |
| **C** | `ActiveDatasourceSupport` 从 Reactor Context 读 | **需要额外桥接**（工具返回 POJO，非 Mono，无法用 `deferContextual`） | 复杂，不如方案B |

**推荐方案B**：per-request Toolkit，线程安全，数据源完全隔离，是 DB-GPT 和 Chat2DB 的主流做法（每次请求携带 datasourceId，后端查出 Datasource 对象传入执行层）。

### 2.3 核心改动清单

| 文件 | 改动类型 | 内容 |
|---|---|---|
| `dto/ChatRequest.java` | 增加字段 | `@NotNull Integer datasourceId` |
| `controller/AgentController.java` | 透传参数 | 把 `datasourceId` 传给 AgentService |
| `agent/AgentService.java` | 中等重构 | 去掉单例 Toolkit；增加 `buildToolkit(Datasource)` 工厂方法 |
| `agent/tools/ExecuteSqlTool.java` | 去 `@Component` | 构造时注入 `Datasource` 对象而非 `ActiveDatasourceSupport` |
| `agent/tools/GetTablesTool.java` | 去 `@Component` | 构造时注入 `Datasource` 对象 |
| `agent/tools/GetTableSchemaTool.java` | 去 `@Component` | 构造时注入 `Datasource` 对象 |
| `service/semantic/TableSemanticService.java` | 增加重载方法 | 带 `datasourceId` 参数的 `getVisibleTablePromptPage(Integer datasourceId, ...)` |
| `service/semantic/impl/TableSemanticServiceImpl.java` | 实现重载 | 绕过 `getActiveDatasource()`，直接用传入的 `datasourceId` |
| `service/ActiveDatasourceSupport.java` | 保留不变 | 仍服务于非 Agent 管理界面 |

`ActiveDatasourceLockManager` 和 `DatasourceServiceImpl` 中的 active 状态管理逻辑保持不变。

### 2.4 核心代码骨架

**ChatRequest.java**（增加字段）：
```java
public record ChatRequest(
    @NotBlank String sessionId,
    @NotBlank String message,
    @NotNull(message = "datasourceId 不能为空") Integer datasourceId) {}
```

**ExecuteSqlTool.java**（去 `@Component`，构造时注入 Datasource）：
```java
// 不再是 Spring Bean，不加 @Component
public class ExecuteSqlTool {
    private final Datasource datasource;
    private final SqlExecutor sqlExecutor;

    public ExecuteSqlTool(Datasource datasource, SqlExecutor sqlExecutor) {
        this.datasource = datasource;
        this.sqlExecutor = sqlExecutor;
    }

    @Tool(name = "execute_sql", description = "...")
    public ToolResult<QueryResult> executeSql(@ToolParam(name = "sql", ...) String sql) {
        try {
            return ToolResult.success(sqlExecutor.execute(datasource, sql));
        } catch (SqlExecutor.SqlSecurityException e) {
            return ToolResult.error("SQL_SECURITY_ERROR", e.getMessage());
        } catch (SqlExecutor.SqlExecutionException e) {
            return ToolResult.error("SQL_EXECUTION_ERROR", e.getMessage());
        }
    }
}
```

**AgentService.java**（per-request Toolkit 工厂）：
```java
@Service
public class AgentService {
    // 去掉三个 Tool 的 Bean 注入，改为依赖可复用组件
    private final ModelFactory modelFactory;
    private final DatasourceService datasourceService;
    private final SqlExecutor sqlExecutor;
    private final TableSemanticService tableSemanticService;
    private final DataSource dataSource; // 管理库 DataSource（会话持久化用）
    // sessionCache 保持不变

    private Toolkit buildToolkit(Datasource datasource) {
        Toolkit tk = new Toolkit();
        tk.registerTool(new ExecuteSqlTool(datasource, sqlExecutor));
        tk.registerTool(new GetTablesTool(datasource, tableSemanticService));
        tk.registerTool(new GetTableSchemaTool(datasource, tableSemanticService));
        return tk;
    }

    public Flux<String> chatStream(String sessionId, String userInput, Integer datasourceId) {
        Datasource datasource = datasourceService.findById(datasourceId);
        if (datasource == null) {
            return Flux.error(new IllegalArgumentException("Datasource not found: " + datasourceId));
        }
        Toolkit toolkit = buildToolkit(datasource);
        ReActAgent agent = createAgent(toolkit);
        // 后续逻辑与现在相同
        MysqlSession session = (MysqlSession) getOrCreateSession(sessionId);
        agent.loadIfExists(session, buildNamespacedSessionId(sessionId));
        // ...
    }

    private ReActAgent createAgent(Toolkit toolkit) {
        return ReActAgent.builder()
            .name("DataAgent")
            .sysPrompt("...")
            .model(modelFactory.createModel())
            .toolkit(toolkit)  // 使用 per-request toolkit
            .memory(new InMemoryMemory())
            .maxIters(16)
            .build();
    }
}
```

### 2.5 SessionKey 是否需要包含 datasourceId

**不推荐将 datasourceId 编入 SessionKey**，原因：
- 对话历史（Message 序列）与具体数据源无关，同一会话可能先查 DB1 再切换查 DB2
- 编入 SessionKey 后切换数据源时旧会话 Message 历史不可读
- 推荐：SessionKey 保持 `data-agent:{sessionId}`，`datasourceId` 仅影响当次工具路由，不写入会话上下文
- 如需强绑定，在第一条消息时将 datasourceId 存入 session 元数据，后续请求校验一致性

---

## 三、结构化 SSE 事件流方案

### 3.1 SSE 协议格式回顾

```
id: <可选ID>\n
event: <事件类型名>\n
data: <JSON 数据>\n
\n
```

`event:` 字段用于区分事件类型，客户端 `addEventListener('reasoning', handler)` 只匹配对应类型的消息。无 `event:` 字段的消息走默认 `onmessage`。

### 3.2 SseEvent DTO 设计

```java
// dto/sse/SseEventType.java
public enum SseEventType {
    REASONING,    // LLM 推理过程（思维链 token，增量）
    TOOL_RESULT,  // 工具调用结果（结构化 JSON）
    FINAL,        // 最终回答（完整内容）
    ERROR         // 错误信息
}

// dto/sse/SseEvent.java
public record SseEvent(
    SseEventType eventType,
    String content,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> metadata
) {
    public static SseEvent reasoning(String chunk) {
        return new SseEvent(SseEventType.REASONING, chunk, null);
    }
    public static SseEvent toolResult(String content, String toolName) {
        return new SseEvent(SseEventType.TOOL_RESULT, content, Map.of("toolName", toolName));
    }
    public static SseEvent finalAnswer(String content) {
        return new SseEvent(SseEventType.FINAL, content, null);
    }
    public static SseEvent error(String message) {
        return new SseEvent(SseEventType.ERROR, message, null);
    }
}
```

### 3.3 AgentService.chatStream() 改造

```java
// 返回类型从 Flux<String> 改为 Flux<ServerSentEvent<SseEvent>>
public Flux<ServerSentEvent<SseEvent>> chatStream(
        String sessionId, String userInput, Integer datasourceId) {

    Datasource datasource = datasourceService.findById(datasourceId);
    if (datasource == null) {
        return Flux.just(sse("error", SseEvent.error("Datasource not found: " + datasourceId)));
    }

    Toolkit toolkit = buildToolkit(datasource);
    ReActAgent agent = createAgent(toolkit);
    MysqlSession session = (MysqlSession) getOrCreateSession(sessionId);
    agent.loadIfExists(session, buildNamespacedSessionId(sessionId));

    Msg userMsg = Msg.builder().textContent(userInput).build();

    StreamOptions streamOptions = StreamOptions.builder()
        .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
        .incremental(true)
        .includeReasoningResult(false)
        .build();

    return agent.stream(userMsg, streamOptions)
        .subscribeOn(Schedulers.boundedElastic())
        .doFinally(signalType -> agent.saveTo(session, buildNamespacedSessionId(sessionId)))
        .map(event -> {
            SseEvent sseEvent = mapToSseEvent(event);
            return sse(sseEvent.eventType().name().toLowerCase(), sseEvent);
        })
        .filter(s -> s.data() != null && s.data().content() != null
                     && !s.data().content().isEmpty())
        .onErrorResume(e ->
            Flux.just(sse("error", SseEvent.error(e.getMessage()))));
}

private SseEvent mapToSseEvent(io.agentscope.core.agent.Event event) {
    String content = MsgUtils.getTextContent(event.getMessage());
    if (content == null || content.isEmpty()) return null;
    return switch (event.getType()) {
        case REASONING   -> SseEvent.reasoning(content);
        case TOOL_RESULT -> SseEvent.toolResult(content, extractToolName(event));
        default          -> SseEvent.finalAnswer(content);
    };
}

// 工厂方法简化 ServerSentEvent 构造
private static ServerSentEvent<SseEvent> sse(String eventType, SseEvent data) {
    return ServerSentEvent.<SseEvent>builder()
        .event(eventType)
        .data(data)
        .build();
}
```

### 3.4 AgentController 返回类型调整

```java
// 同步接口（保持不变，AgentService.chat() 返回 String）
@PostMapping("/chat")
public Result<String> chat(@Valid @RequestBody ChatRequest request) {
    String response = agentService.chat(
        request.sessionId(), request.message(), request.datasourceId());
    return Result.success(response);
}

// 流式接口：返回类型变更
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<SseEvent>> chatStream(@Valid @RequestBody ChatRequest request) {
    return agentService.chatStream(
        request.sessionId(), request.message(), request.datasourceId());
}
```

### 3.5 实际 SSE 输出格式

```
event: reasoning
data: {"eventType":"REASONING","content":"正在分析用户问题，选择相关表..."}

event: tool_result
data: {"eventType":"TOOL_RESULT","content":"[{\"id\":1,\"name\":\"张三\"}]","metadata":{"toolName":"execute_sql"}}

event: final
data: {"eventType":"FINAL","content":"查询结果共 1 条记录：用户张三"}

```

### 3.6 前端消费伪代码（支持 POST 的 SSE）

标准 `EventSource` 不支持 POST，需用 `fetch` 手动解析：

```javascript
async function chatStream(sessionId, message, datasourceId) {
    const response = await fetch('/api/agent/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId, message, datasourceId }),
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const events = buffer.split('\n\n');
        buffer = events.pop(); // 保留未完整的最后段

        for (const rawEvent of events) {
            const lines = rawEvent.split('\n');
            let eventType = 'message', data = '';
            for (const line of lines) {
                if (line.startsWith('event: ')) eventType = line.slice(7).trim();
                if (line.startsWith('data: '))  data = line.slice(6).trim();
            }
            if (!data) continue;
            const payload = JSON.parse(data);
            handleSseEvent(eventType, payload);
        }
    }
}

function handleSseEvent(eventType, payload) {
    switch (eventType) {
        case 'reasoning':
            // 追加到"推理过程"折叠面板（流式渲染）
            appendReasoningChunk(payload.content);
            break;
        case 'tool_result':
            // 渲染工具调用卡片（工具名 + 结果 JSON）
            renderToolCard(payload.metadata?.toolName, payload.content);
            break;
        case 'final':
            // 渲染最终回答（支持 Markdown）
            renderFinalAnswer(payload.content);
            break;
        case 'error':
            showErrorMessage(payload.content);
            break;
    }
}
```

---

## 四、改动影响范围评估与建议实施顺序

### 先实现方向1（多数据源路由），再实现方向2（SSE 结构化）

方向1会修改 `AgentService.chatStream()` 的签名（增加 `datasourceId` 参数）。如果先改方向2，稍后方向1会再次修改同一方法，产生合并冲突。两者先后顺序明确为：**路由 → SSE**。

### 工作量估算

| 方向 | 涉及文件数 | 工作量 |
|---|---|---|
| 多数据源路由（方案B） | 8 个文件修改 | 中（1-2天，主要在 TableSemanticServiceImpl 的重载方法） |
| 结构化 SSE | 3 个文件修改 + 2 个新增 DTO | 小（半天，主要是返回类型变更和 DTO 新增） |

### 关键注意事项

1. `GetTablesTool` 和 `GetTableSchemaTool` 去掉 `@Component` 后，`@PostConstruct` 中的 `toolkit.registerTool()` 调用需要移走，改为在 `buildToolkit(Datasource)` 中动态注册
2. `TableSemanticServiceImpl` 新增带 `datasourceId` 参数的重载时，注意 `SemanticCatalog.CatalogContext` 的 `datasource` 字段是如何传入的——确认 `SemanticCatalog.createContext(Datasource)` 路径完整
3. SSE 流中 FINAL 事件的来源：当前 `StreamOptions.includeReasoningResult(false)` + 只订阅 `REASONING/TOOL_RESULT`，最终 answer 不会流出。有两个选项：
   - 改为 `includeReasoningResult(true)` 并在 `mapToSseEvent` 里把最后一个非空文本事件映射为 FINAL
   - 在 `doFinally` 后使用 `concatWith(Mono.fromCallable(() -> agent 最终状态))` 补发 FINAL 事件

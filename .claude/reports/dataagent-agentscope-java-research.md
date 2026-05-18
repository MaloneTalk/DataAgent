# DataAgent 与 AgentScope-Java 框架深度适配方案

生成时间：2026-05-17（已验证源码）  
调研来源：AgentScope-Java GitHub 源码（已验证）、DataAgent 当前代码实现、四份专项调研报告

---

## 一、AgentScope-Java 能力全景图（源码验证）

### 1.1 核心包结构（已通过 GitHub 源码确认）

| 包路径 | 核心类 | 用途 | 验证状态 |
|---|---|---|---|
| `io.agentscope.core.agent` | `ReActAgent`, `AgentBase`, `UserAgent` | Agent 实现与生命周期管理 | ✅ 已验证 |
| `io.agentscope.core.model` | `DashScopeChatModel`, `OpenAIChatModel`, `AnthropicChatModel` | LLM 提供商集成 | ✅ 已验证 |
| `io.agentscope.core.tool` | `Toolkit`, `AgentTool`, `@Tool`, `ToolGroup`, `ToolGroupManager` | 工具注册、执行、**动态分组** | ✅ 已验证 |
| `io.agentscope.core.memory` | `Memory`, `InMemoryMemory`, `LongTermMemory` | 对话历史与长期存储 | ✅ 已验证 |
| `io.agentscope.core.message` | `Msg`, `ContentBlock`, `TextBlock`, `ToolUseBlock`, `ToolResultBlock` | 消息结构与内容类型 | ✅ 已验证 |
| `io.agentscope.core.plan` | `PlanNotebook`, `Plan`, `SubTask` | 任务规划与分解 | ✅ 已验证 |
| `io.agentscope.core.tool.mcp` | `McpClientBuilder`, `McpClientWrapper` | Model Context Protocol 客户端 | ✅ 已验证 |
| `io.agentscope.core.agui` | `AguiEvent`, `AguiAgentAdapter`, `AguiAdapterConfig` | **AG-UI 协议扩展** | ✅ 已验证 |

### 1.2 关键发现：Hook System 不存在

**重要**：通过 GitHub 代码搜索确认，`io.agentscope.core.hook` 包中的 `PreToolHook`/`PostToolHook` **不存在**。之前基于 DeepWiki TOC 的推测是错误的。

**替代方案**：
- 工具执行回调：`Toolkit.setChunkCallback(BiConsumer<ToolUseBlock, ToolResultBlock>)`
- 内部回调：`Toolkit.setInternalChunkCallback()` 用于框架组件

### 1.3 高级特性清单（已验证）

| 特性 | 验证状态 | 关键 API |
|---|---|---|
| **ToolGroup 动态分组** | ✅ 已验证 | `ToolGroup.setActive(boolean)`, `Toolkit.createToolGroup()`, `Toolkit.updateToolGroups()` |
| **AG-UI Protocol** | ✅ 已验证 | `AguiEvent` sealed interface（19 种事件类型）, `AguiAgentAdapter` |
| **MCP Protocol** | ✅ 已验证 | `Toolkit.registerMcpClient(McpClientWrapper)` |
| **Tool Chunk Callback** | ✅ 已验证 | `Toolkit.setChunkCallback(BiConsumer<ToolUseBlock, ToolResultBlock>)` |
| **Meta Tool** | ✅ 已验证 | `Toolkit.registerMetaTool()` 注册 `reset_equipped_tools` 工具 |
| **SubAgent Tool** | ✅ 已验证 | `ToolRegistration.subAgent(SubAgentProvider, SubAgentConfig)` |
| **Hook System** | ❌ 不存在 | 搜索返回 0 结果，需用 Chunk Callback 替代 |

---

## 二、DataAgent 当前使用现状

### 2.1 已使用能力（AgentService.java:20-162）

```java
// 已使用的 AgentScope-Java 类
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
```

**使用模式**：
- **Agent 类型**：仅 `ReActAgent`（单 Agent，无协作）
- **工具管理**：单例 `Toolkit`，`@PostConstruct` 注册三个工具（`GetTablesTool`, `GetTableSchemaTool`, `ExecuteSqlTool`）
- **Memory**：`InMemoryMemory`（会话内短期记忆）
- **Session**：`MysqlSession`（持久化到 `data_agent.agentscope_sessions` 表）
- **流式输出**：`StreamOptions.builder().eventTypes(REASONING, TOOL_RESULT).incremental(true).includeReasoningResult(false)`
- **事件类型**：仅订阅 `REASONING` 和 `TOOL_RESULT`，未分类输出（AgentService.java:111 TODO 注释）

### 2.2 未使用能力（高价值）

| 能力 | 当前状态 | 潜在价值 |
|---|---|---|
| **Hook System** | 未使用 | 替代 AOP 实现审计日志、AuditContext 传递 |
| **ToolGroup** | 未使用 | 解决 per-request datasource Toolkit 问题 |
| **Structured Output** | 未使用 | 类型安全的工具返回值解析 |
| **AG-UI Protocol** | 未使用 | 原生结构化 SSE 事件流（替代手动 SseEvent 封装） |
| **PlanNotebook** | 未使用 | 显式查询计划生成与分步执行 |
| **LongTermMemory** | 未使用 | 跨会话语义层知识记忆 |
| **MCP Protocol** | 未使用 | 插件化工具扩展 |
| **Spring Boot Starters** | 未使用 | 简化配置（当前手动构建 ReActAgent） |

---

## 三、四大痛点与 AgentScope-Java 原生解法（已验证）

### 3.1 痛点1：多数据源路由（per-request Toolkit）

**当前方案**（来自 `dataagent-routing-sse-research.md`）：
- 方案B（推荐）：每次请求动态创建 Toolkit，工具去 `@Component`

**AgentScope-Java 原生解法：ToolGroup 动态分组（✅ 已验证）**

`ToolGroup.java` 源码确认支持：
```java
// ToolGroup 核心 API（已验证）
ToolGroup.builder()
    .name("datasource-1")
    .description("数据源1的工具组")
    .active(false)  // 初始不激活
    .build();

toolGroup.setActive(true);   // 动态激活
toolGroup.setActive(false);  // 动态停用
toolGroup.addTool("execute_sql");
toolGroup.containsTool("execute_sql");
```

`Toolkit.java` 源码确认支持：
```java
// Toolkit 工具组管理 API（已验证）
toolkit.createToolGroup("datasource-1", "数据源1工具组", false);
toolkit.updateToolGroups(List.of("datasource-1"), true);  // 批量激活
toolkit.getActiveGroups();  // 获取当前激活的组
toolkit.setActiveGroups(List.of("datasource-1"));  // 设置激活组
toolkit.getToolGroup("datasource-1");  // 获取组对象
```

**DataAgent 落地方案**：

```java
// AgentService.java 改造
@Service
public class AgentService {
    private Toolkit toolkit;
    
    @PostConstruct
    public void init() {
        this.toolkit = new Toolkit();
        // 为每个数据源创建工具组（初始不激活）
        for (Datasource ds : datasourceService.findAll()) {
            String groupName = "ds-" + ds.getId();
            toolkit.createToolGroup(groupName, ds.getName(), false);
            
            // 注册工具到组
            toolkit.registration()
                .tool(new ExecuteSqlTool(ds, sqlExecutor))
                .group(groupName)
                .apply();
            toolkit.registration()
                .tool(new GetTablesTool(ds, tableSemanticService))
                .group(groupName)
                .apply();
            toolkit.registration()
                .tool(new GetTableSchemaTool(ds, tableSemanticService))
                .group(groupName)
                .apply();
        }
    }
    
    public Flux<String> chatStream(String sessionId, String userInput, Integer datasourceId) {
        // 动态切换到目标数据源的工具组
        String targetGroup = "ds-" + datasourceId;
        toolkit.setActiveGroups(List.of(targetGroup));
        
        ReActAgent agent = createAgent();
        // ... 后续逻辑不变
    }
}
```

**优势**：
- 无需每次请求重建 Toolkit
- 工具实例可复用（减少 GC 压力）
- 框架级别的工具隔离

---

### 3.2 痛点2：结构化 SSE 事件流

**当前方案**（来自 `dataagent-routing-sse-research.md`）：
- 手动定义 `SseEvent` record
- 手动 `map(event -> sse(...))`

**AgentScope-Java 原生解法：AG-UI Protocol（✅ 已验证）**

`AguiEvent.java` 源码确认是 **sealed interface**，包含 19 种事件类型：

```java
// AguiEvent 事件类型（已验证）
public sealed interface AguiEvent permits
    AguiEvent.RunStarted,           // 运行开始
    AguiEvent.RunFinished,          // 运行结束
    AguiEvent.TextMessageStart,     // 文本消息开始
    AguiEvent.TextMessageContent,   // 文本消息内容（增量）
    AguiEvent.TextMessageEnd,       // 文本消息结束
    AguiEvent.ToolCallStart,        // 工具调用开始
    AguiEvent.ToolCallArgs,         // 工具调用参数（增量）
    AguiEvent.ToolCallEnd,          // 工具调用结束
    AguiEvent.ToolCallResult,       // 工具调用结果
    AguiEvent.StateSnapshot,        // 状态快照
    AguiEvent.StateDelta,           // 状态增量（JSON Patch）
    AguiEvent.Raw,                  // 原始事件
    AguiEvent.Custom,               // 自定义事件
    AguiEvent.ReasoningStart,       // 推理开始
    AguiEvent.ReasoningMessageStart,
    AguiEvent.ReasoningMessageContent,
    AguiEvent.ReasoningMessageEnd,
    AguiEvent.ReasoningMessageChunk,
    AguiEvent.ReasoningEnd          // 推理结束
{ ... }
```

`AguiAgentAdapter.java` 源码确认提供 **Agent → AG-UI 事件转换**：

```java
// AguiAgentAdapter 核心 API（已验证）
AguiAgentAdapter adapter = new AguiAgentAdapter(agent, AguiAdapterConfig.builder()
    .enableReasoning(true)      // 启用推理事件
    .emitToolCallArgs(true)     // 发送工具参数增量
    .build());

Flux<AguiEvent> events = adapter.run(RunAgentInput.builder()
    .threadId(sessionId)
    .runId(UUID.randomUUID().toString())
    .messages(messages)
    .build());
```

**DataAgent 落地方案**：

```java
// AgentController.java 改造
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<AguiEvent>> chatStream(@Valid @RequestBody ChatRequest request) {
    return agentService.chatStreamAgui(
        request.sessionId(), request.message(), request.datasourceId());
}

// AgentService.java 新增方法
public Flux<ServerSentEvent<AguiEvent>> chatStreamAgui(
        String sessionId, String userInput, Integer datasourceId) {
    
    String targetGroup = "ds-" + datasourceId;
    toolkit.setActiveGroups(List.of(targetGroup));
    
    ReActAgent agent = createAgent();
    MysqlSession session = (MysqlSession) getOrCreateSession(sessionId);
    agent.loadIfExists(session, buildNamespacedSessionId(sessionId));
    
    // 使用 AG-UI Adapter
    AguiAgentAdapter adapter = new AguiAgentAdapter(agent, 
        AguiAdapterConfig.builder()
            .enableReasoning(true)
            .emitToolCallArgs(true)
            .build());
    
    RunAgentInput input = RunAgentInput.builder()
        .threadId(sessionId)
        .runId(UUID.randomUUID().toString())
        .messages(List.of(new AguiMessage("user", userInput)))
        .build();
    
    return adapter.run(input)
        .doFinally(sig -> agent.saveTo(session, buildNamespacedSessionId(sessionId)))
        .map(event -> ServerSentEvent.<AguiEvent>builder()
            .event(event.getType().name().toLowerCase())
            .data(event)
            .build());
}
```

**pom.xml 新增依赖**：
```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-agui</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

**优势**：
- 标准化 19 种事件类型，前端可按类型分别处理
- 自动处理事件状态跟踪（开始/结束配对）
- 支持推理过程展示（ReasoningMessage 系列事件）

---

### 3.3 痛点3：审计日志与 AuditContext 传递

**当前方案**（来自 `dataagent-security-execution-research.md`）：
- AOP 拦截 `SqlExecutor.execute()`
- `AuditContext` 用 ThreadLocal 传递

**AgentScope-Java 原生解法：Chunk Callback（✅ 已验证，替代 Hook）**

`Toolkit.java` 源码确认支持工具执行回调：

```java
// Toolkit Chunk Callback API（已验证）
toolkit.setChunkCallback((ToolUseBlock toolUse, ToolResultBlock result) -> {
    // 每次工具执行完成后回调
    // toolUse: 工具调用信息（名称、参数）
    // result: 工具执行结果
});
```

**DataAgent 落地方案**：

```java
// AgentService.java 改造
@PostConstruct
public void init() {
    this.toolkit = new Toolkit();
    // ... 工具注册逻辑
    
    // 设置审计回调
    toolkit.setChunkCallback((toolUse, result) -> {
        if ("execute_sql".equals(toolUse.getName())) {
            SqlAuditLog log = new SqlAuditLog();
            log.setDatasourceId(extractDatasourceId(toolUse));
            log.setSqlText(extractSqlParam(toolUse));
            log.setStatus(result.isError() ? "FAILED" : "SUCCESS");
            log.setExecuteTime(LocalDateTime.now());
            // 异步写入
            auditLogService.saveAsync(log);
        }
    });
}

private String extractSqlParam(ToolUseBlock toolUse) {
    Map<String, Object> input = toolUse.getInput();
    return input != null ? (String) input.get("sql") : null;
}
```

**优势**：
- 无需 AOP，框架原生支持
- 回调在工具执行完成后触发，可获取完整的输入和输出
- 同步/流式路径统一处理

**局限**：
- Chunk Callback 无法获取用户原始问题（需从 session 或上下文补充）
- 建议：在 `chatStream()` 开始时将 `userInput` 存入 session 元数据，回调中读取

---

### 3.4 痛点4：测试中的 LLM Mock

**当前方案**（来自 `dataagent-testing-implementation-research.md`）：
- 纯 Mockito Mock `ModelFactory` 和 `ChatModel`

**AgentScope-Java 原生解法：暂无官方 MockChatModel**

通过 GitHub 搜索确认，AgentScope-Java **暂未提供官方 MockChatModel**。

**推荐方案**：继续使用 Mockito，但可利用 `Toolkit.copy()` 简化测试：

```java
// AgentServiceTest.java
@Test
void testChatStream() {
    // Mock ChatModel
    ChatModel mockModel = mock(ChatModel.class);
    when(mockModel.generate(any())).thenReturn(Mono.just(
        Msg.builder().textContent("查询结果：用户张三").build()));
    
    // 使用 Toolkit.copy() 创建测试用 Toolkit
    Toolkit testToolkit = productionToolkit.copy();
    
    // 构建测试 Agent
    ReActAgent agent = ReActAgent.builder()
        .model(mockModel)
        .toolkit(testToolkit)
        .memory(new InMemoryMemory())
        .build();
    
    // 执行测试
    Msg response = agent.call(Msg.builder().textContent("查询用户表").build()).block();
    assertThat(response).isNotNull();
}

---

## 四、其他高价值能力评估

### 4.1 PlanNotebook（章节 9.2）

**能力**：任务规划与分解（`Plan`, `SubTask`, Hint Generation System）

**DataAgent 适用场景**：
- 复杂查询自动分解（如"对比去年同期销售额"需拆分为：查表 → 生成两条 SQL → 结果对比）
- 显式查询计划展示给用户（类似 SQL EXPLAIN）

**落地优先级**：P2（当前 ReActAgent 隐式规划已基本够用，显式规划是增强体验）

**推测 API**：
```java
PlanNotebook notebook = PlanNotebook.create();
Plan plan = notebook.generatePlan(userInput, toolkit);
for (SubTask task : plan.getSubTasks()) {
    Msg result = agent.call(task.toMsg()).block();
    notebook.recordResult(task.getId(), result);
}
String finalAnswer = notebook.synthesize();
```

---

### 4.2 LongTermMemory（章节 5.2）

**能力**：跨会话长期记忆存储

**DataAgent 适用场景**：
- 记住用户常用表、常用查询模式
- 语义层知识缓存（表描述、列语义、关系语义）

**落地优先级**：P2（当前 `InMemoryMemory` + `MysqlSession` 已支持会话内记忆，跨会话记忆是优化）

**推测 API**：
```java
LongTermMemory ltm = LongTermMemory.builder()
    .storage(new RedisMemoryStorage()) // 或 MySQL
    .build();

ReActAgent.builder()
    .memory(ltm)
    .build();

// 自动从 LongTermMemory 加载历史知识
```

---

### 4.3 MCP Protocol（章节 6）

**能力**：Model Context Protocol 客户端，集成外部工具

**DataAgent 适用场景**：
- 集成外部数据源（如 API、文件系统）
- 插件化扩展工具（用户自定义工具）

**落地优先级**：P3（当前三个工具已满足核心需求，MCP 是长期扩展方向）

**推测 API**：
```java
McpClient mcpClient = McpClientBuilder.create()
    .serverUrl("http://localhost:8080/mcp")
    .build();

Toolkit toolkit = new Toolkit();
toolkit.registerMcpTools(mcpClient); // 自动发现并注册 MCP 工具
```

---

### 4.4 Spring Boot Starters（章节 11.1）

**能力**：
- `agentscope-spring-boot-starter-core`：自动配置 Agent、Model、Toolkit
- `agentscope-spring-boot-starter-ag-ui`：自动配置 AG-UI Protocol SSE 端点
- `agentscope-spring-boot-starter-chat-completions-web`：OpenAI-compatible API

**DataAgent 适用场景**：
- 简化 `AgentService` 配置（当前手动 `@PostConstruct` 注册工具）
- 自动配置 SSE 端点（替代手动 `AgentController.chatStream()`）

**落地优先级**：P1（配置简化，减少样板代码）

**推测配置**：
```yaml
# application.yml
agentscope:
  agent:
    name: DataAgent
    type: react
    max-iters: 16
  model:
    provider: dashscope
    api-key: ${DASHSCOPE_API_KEY}
  toolkit:
    auto-scan: true # 自动扫描 @Tool 注解
```

---

## 五、实施路线图（基于已验证 API）

### Phase 1：核心能力落地（P0，1 周）

| 任务 | 当前方案 | AgentScope-Java 方案 | 工作量 | 验证状态 |
|---|---|---|---|---|
| 1.1 ToolGroup 多数据源路由 | per-request Toolkit | `Toolkit.createToolGroup()` + `setActiveGroups()` | 中（2-3天） | ✅ API 已验证 |
| 1.2 AG-UI Protocol SSE | 手动 `SseEvent` | `AguiAgentAdapter` + `AguiEvent` | 中（2-3天） | ✅ API 已验证 |
| 1.3 Chunk Callback 审计 | AOP + ThreadLocal | `Toolkit.setChunkCallback()` | 小（1天） | ✅ API 已验证 |

**验收标准**：
- `ChatRequest` 增加 `datasourceId`，工具自动路由到对应数据源
- SSE 返回 `AguiEvent` 类型，前端可按 `event.getType()` 分类处理
- SQL 执行审计日志在同步/流式路径统一生效

**新增依赖**：
```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-agui</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

---

### Phase 2：架构增强（P1，1-2 周）

| 任务 | AgentScope-Java 方案 | 工作量 |
|---|---|---|
| 2.1 Meta Tool 动态工具管理 | `Toolkit.registerMetaTool()` | 小（半天） |
| 2.2 SubAgent 子代理工具 | `ToolRegistration.subAgent()` | 中（2天） |
| 2.3 MCP Protocol 外部工具 | `Toolkit.registerMcpClient()` | 中（2天） |

**验收标准**：
- Agent 可通过 `reset_equipped_tools` 工具动态切换工具组
- 支持注册子代理作为工具（如专门的 SQL 优化代理）
- 支持通过 MCP 协议集成外部工具

---

### Phase 3：高级特性（P2，按需）

| 任务 | AgentScope-Java 方案 | 工作量 |
|---|---|---|
| 3.1 PlanNotebook 查询计划 | `PlanNotebook` + `SubTask` | 大（1-2周） |
| 3.2 LongTermMemory 跨会话记忆 | `LongTermMemory` | 中（1周） |
| 3.3 StateSnapshot 状态同步 | `AguiEvent.StateSnapshot` | 小（2天） |

---

## 六、关键代码改动清单

### 6.1 AgentService.java 改造要点

```java
// 改动1：工具组初始化
@PostConstruct
public void init() {
    this.toolkit = new Toolkit();
    
    // 为每个数据源创建工具组
    for (Datasource ds : datasourceService.findAll()) {
        String groupName = "ds-" + ds.getId();
        toolkit.createToolGroup(groupName, ds.getName(), false);
        
        toolkit.registration()
            .tool(new ExecuteSqlTool(ds, sqlExecutor))
            .group(groupName)
            .apply();
        // ... 其他工具
    }
    
    // 设置审计回调
    toolkit.setChunkCallback(this::auditToolExecution);
}

// 改动2：流式接口使用 AG-UI Adapter
public Flux<ServerSentEvent<AguiEvent>> chatStreamAgui(
        String sessionId, String userInput, Integer datasourceId) {
    
    toolkit.setActiveGroups(List.of("ds-" + datasourceId));
    
    ReActAgent agent = createAgent();
    AguiAgentAdapter adapter = new AguiAgentAdapter(agent, 
        AguiAdapterConfig.builder().enableReasoning(true).build());
    
    return adapter.run(buildRunInput(sessionId, userInput))
        .map(event -> ServerSentEvent.<AguiEvent>builder()
            .event(event.getType().name().toLowerCase())
            .data(event)
            .build());
}

// 改动3：审计回调
private void auditToolExecution(ToolUseBlock toolUse, ToolResultBlock result) {
    if ("execute_sql".equals(toolUse.getName())) {
        auditLogService.saveAsync(SqlAuditLog.builder()
            .sqlText((String) toolUse.getInput().get("sql"))
            .status(result.isError() ? "FAILED" : "SUCCESS")
            .build());
    }
}
```

### 6.2 AgentController.java 改造要点

```java
// 新增 AG-UI 流式接口
@PostMapping(value = "/chat/stream/agui", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<AguiEvent>> chatStreamAgui(
        @Valid @RequestBody ChatRequest request) {
    return agentService.chatStreamAgui(
        request.sessionId(), request.message(), request.datasourceId());
}
```

### 6.3 ChatRequest.java 改造要点

```java
public record ChatRequest(
    @NotBlank String sessionId,
    @NotBlank String message,
    @NotNull(message = "datasourceId 不能为空") Integer datasourceId
) {}
```

---

## 七、前端适配指南

### 7.1 AG-UI 事件消费示例

```javascript
async function chatStreamAgui(sessionId, message, datasourceId) {
    const response = await fetch('/api/agent/chat/stream/agui', {
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
        buffer = events.pop();

        for (const rawEvent of events) {
            const { eventType, data } = parseSSE(rawEvent);
            handleAguiEvent(eventType, data);
        }
    }
}

function handleAguiEvent(eventType, data) {
    switch (eventType) {
        case 'run_started':
            console.log('Agent 开始运行', data.runId);
            break;
        case 'text_message_content':
            appendToChat(data.delta);  // 增量文本
            break;
        case 'tool_call_start':
            showToolCard(data.toolCallName, 'loading');
            break;
        case 'tool_call_result':
            updateToolCard(data.toolCallId, data.content);
            break;
        case 'reasoning_message_content':
            appendToReasoningPanel(data.delta);  // 推理过程
            break;
        case 'run_finished':
            console.log('Agent 运行结束');
            break;
    }
}
```

## 八、风险与注意事项

### 8.1 ToolGroup 线程安全

**风险**：`toolkit.setActiveGroups()` 修改的是 Toolkit 实例的状态，多个并发请求可能互相干扰。

**缓解措施**：
```java
// 方案A：每次请求 copy Toolkit（推荐）
public Flux<...> chatStream(...) {
    Toolkit requestToolkit = this.toolkit.copy();  // 深拷贝
    requestToolkit.setActiveGroups(List.of("ds-" + datasourceId));
    ReActAgent agent = createAgent(requestToolkit);
    // ...
}

// 方案B：使用 ThreadLocal 存储当前请求的 Toolkit
```

### 8.2 AG-UI 扩展模块版本

**注意**：`agentscope-extensions-agui` 是独立模块，需确认与 `agentscope-core` 版本兼容。

```xml
<!-- 建议使用 BOM 统一管理版本 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.agentscope</groupId>
            <artifactId>agentscope-bom</artifactId>
            <version>${agentscope.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 8.3 Chunk Callback 局限

**局限**：`setChunkCallback()` 只能设置一个回调，多个关注点（审计、监控、日志）需在同一回调中处理。

**建议**：封装为 `CompositeToolCallback`：
```java
public class CompositeToolCallback implements BiConsumer<ToolUseBlock, ToolResultBlock> {
    private final List<BiConsumer<ToolUseBlock, ToolResultBlock>> callbacks = new ArrayList<>();
    
    public void add(BiConsumer<ToolUseBlock, ToolResultBlock> callback) {
        callbacks.add(callback);
    }
    
    @Override
    public void accept(ToolUseBlock toolUse, ToolResultBlock result) {
        callbacks.forEach(cb -> cb.accept(toolUse, result));
    }
}
```

---

## 九、总结

### 9.1 核心发现（已验证）

| 发现 | 状态 | 影响 |
|---|---|---|
| ToolGroup 支持动态激活/停用 | ✅ 已验证 | 可替代 per-request Toolkit 方案 |
| AG-UI Protocol 提供 19 种事件类型 | ✅ 已验证 | 可替代手动 SseEvent 封装 |
| AguiAgentAdapter 自动转换事件 | ✅ 已验证 | 大幅简化 SSE 实现 |
| Chunk Callback 支持工具执行回调 | ✅ 已验证 | 可替代 AOP 审计（部分） |
| Hook System 不存在 | ❌ 不存在 | 需用 Chunk Callback 替代 |
| MockChatModel 不存在 | ❌ 不存在 | 继续使用 Mockito |

### 9.2 优先级建议（已调整）

**立即开工（P0，1 周）**：
1. ToolGroup 多数据源路由（API 已验证，可直接实施）
2. AG-UI Protocol SSE（API 已验证，可直接实施）
3. Chunk Callback 审计（API 已验证，可直接实施）

**近期规划（P1）**：
- Meta Tool 动态工具管理
- SubAgent 子代理工具
- MCP Protocol 外部工具

**长期演进（P2-P3）**：
- PlanNotebook 查询计划
- LongTermMemory 跨会话记忆

### 9.3 与既有调研报告的关系

| 既有报告 | 本报告对应章节 | 结论 |
|---|---|---|
| `dataagent-routing-sse-research.md` | 三.1、三.2 | **ToolGroup + AG-UI 替代手动方案** |
| `dataagent-security-execution-research.md` | 三.3 | **Chunk Callback 部分替代 AOP**（用户问题仍需补充） |
| `dataagent-testing-implementation-research.md` | 三.4 | **继续使用 Mockito**（无官方 MockChatModel） |

---

## 十、附录：AgentScope-Java 关键源码引用

| 文件 | 路径 | 关键 API |
|---|---|---|
| ToolGroup.java | `agentscope-core/.../tool/ToolGroup.java` | `setActive()`, `addTool()`, `containsTool()` |
| Toolkit.java | `agentscope-core/.../tool/Toolkit.java` | `createToolGroup()`, `setActiveGroups()`, `setChunkCallback()`, `copy()` |
| AguiEvent.java | `agentscope-extensions-agui/.../event/AguiEvent.java` | 19 种事件类型 sealed interface |
| AguiAgentAdapter.java | `agentscope-extensions-agui/.../adapter/AguiAgentAdapter.java` | `run(RunAgentInput)` 返回 `Flux<AguiEvent>` |

# 架构

本文说明 Data Agent 的系统组成、一次查询请求是如何流转的，以及主要模块与对外接口。

## 1. 系统组成

| 组件 | 技术 | 职责 |
| --- | --- | --- |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus | 聊天界面、数据源管理、语义管理、资产与数据证据、系统管理 |
| 后端 | Spring Boot 4.0.3（Java 17+）、AgentScope 1.0.11、MyBatis-Plus | Agent 推理、工具执行、语义层、数据源、会话、权限、MCP 配置管理 |
| Skill 系统 | 多源加载（文件系统 / Git / Nacos / classpath） | 加载"已验证查询模式"作为可复用流程 |
| 元数据库 | MySQL | 存语义层、数据源、会话、MCP 配置、报表、用户、角色与权限等 |
| 查询数据源 | JDBC 关系型数据库 | 用户真正要查的业务库，由 Agent 动态连接；默认内置 MySQL 驱动，其他类型需添加驱动并验证 SQL 方言 |

> 后端根包名为 `io.github.malonetalk`，应用名 `data-agent-management`，默认端口 `8080`。

## 2. 一次查询请求的流转

```
┌─────────┐   ① 提问(SSE)    ┌──────────────┐
│ 前端     │ ───────────────► │ AgentController│  POST /api/agent/chat/stream
└─────────┘                  └──────┬───────┘
                                    │ ② 进入 ReAct 循环
                                    ▼
                          ┌──────────────────┐
                          │   AgentService    │
                          │  (LLM + 工具调用)  │
                          └──┬─────┬─────┬────┘
               ③ 取语义层    │     │     │ ④ 执行 SQL
                   (MySQL)  │     │     ▼
                            ▼     │  ┌──────────────┐
                   get_domains/  │  │ 目标数据源     │
                   get_tables/   │  │(JDBC 兼容数据库)│
                   get_table_    │  └──────────────┘
                   schema        │
                            │     │ 反问用户(ask_user)
                            ▼     ▼
                    ⑤ 流式返回 (Server-Sent-Event) ─────► 前端逐字渲染
```

要点：

- 前端通过 `POST /api/agent/chat/stream` 以 **SSE（Server-Sent Events）** 接收流式回答。
- 新会话先调用 `POST /api/agent/session`，服务端登记用户归属并返回 `AGENTSCOPE_<userId>_<UUID>` 格式的 ID；SSE 必须使用该 ID。客户端生成的随机 ID 不会自动登记为本人会话。
- 后端 `AgentService` 维护一个 **ReAct 循环**：LLM 决定调用哪个工具 → 工具执行 → 结果回灌 → LLM 继续，直到给出最终答案。
- 工具执行 SQL 时，连接到用户在「数据源管理」中配置的目标库，而非元数据库。
- 语义层（域/表/列/关系/指标口径）在需要时从元数据库按需读取，用于把自然语言映射到物理表与字段，并校正指标口径。
- 指标的计算方式、结果类型、单位、倍率和币种随 `get_metric_caliber` 一起提供给 Agent。后端在保存时校验并归一化这些属性，由 Agent 按口径生成 SQL 和解释结果；SQL 执行器不自动应用倍率、换汇或改写快照与比率计算。
- Agent 工具链按 `get_domains` → `get_tables` → `get_table_schema` 递进取数：领域返回名称与描述，表返回领域、说明与已启用关系，字段返回同步后的类型、主键、索引提示与业务描述。
- 权限由 HTTP 入口和敏感工具分别检查。普通用户的 SQL 在执行前按表白名单、隐藏列黑名单改写；查询和导出共用 SELECT 解析与授权路径。详细规则见 [permissions.md](permissions.md)。

### 会话与数据源协议

`POST /api/agent/session` 无需请求体，成功响应的 `data` 是会话 ID 字符串。随后发送：

```json
{
  "sessionId": "AGENTSCOPE_1_<服务端返回的UUID>",
  "message": "上个月各区域销售额是多少？",
  "datasourceIds": [1, 2]
}
```

`datasourceIds` 为本会话允许使用的数据源列表；省略或传空数组表示当前用户所有可访问的启用数据源。服务端会按用户当前表授权过滤，并把解析后的列表保存到 `session_datasource`（逻辑删除旧范围，一行一个数据源）。每次请求都可以更新该列表。

工具调用采用显式数据源：存在多个可选数据源时，`get_tables`、`get_table_schema`、`get_metric_caliber`、`execute_sql` 和 `export_table` 必须传 `datasource_id`；只有一个数据源时可以省略。`get_datasources` 用于列出本会话可用的数据源。不同数据源的同名表不能混用。数据源权限在每次工具调用时重新检查，撤权或删除后会拒绝后续操作。

会话列表仅展示 `AGENTSCOPE_` 命名空间且已有会话内容的记录。无此前缀的旧会话不会出现在列表中；创建接口登记归属后，空会话也不会立即出现在历史列表。历史、调试与删除接口使用数据库中的归属记录检查访问，不通过解析 ID 推断权限。

`ask_user` 的回复通过同一 SSE 接口的 `toolResults` 提交，每项包含 `toolCallId`、`toolName`、`output`；仅接受属于该会话的 `ask_user` 调用。流事件包括 `thinking`、`text`、`tool_call`、`tool_result`、`question`、`report`、`summary` 和 `error`，错误事件包含 `errorCode`。

### 数据证据

`agent.provenance` 从服务端工具事件构建证据，并按 `(sessionId, answerId)` 保存到 `answer_provenance`。调用 `execute_sql`、`export_table`、`get_metric_caliber` 或 `execute_python` 时会生成记录；正常结束标记 `COMPLETE`，中断或异常标记 `PARTIAL`，只进行纯文本回答时不一定有证据。

证据包含原始 SQL、实际执行 SQL、返回行数与截断标志、指标引用、Python 执行状态，以及可解析的表列来源。表列解析使用执行时的授权列快照，缺少快照的旧结果仅保留执行记录并写入警告；Python 内联数据与 SQL 结果尚未逐值核对。证据展示入口为「资产管理 → 数据证据」，分页接口为 `GET /api/agent/evidence`，可按 `sessionId`、`answerId` 过滤。

## 3. 后端模块划分（按职责）

| 包 / 模块 | 说明 |
| --- | --- |
| `agent` | Agent 主循环、会话、数据源连接管理、模型工厂与提供商 |
| `agent.models` | LLM 提供商抽象：OpenAI / Ollama / DashScope / Anthropic |
| `agent.tools` | 工具集：SQL、Python、schema、域/表、指标口径、反问、报表、CSV 导出、日期 |
| `agent.skill` | Skill 多源加载（文件/Git/Nacos/classpath） |
| `agent.provenance` | 回答证据构建与合并（`AnswerProvenanceBuilder`）、存储与查询、SQL 表列来源解析 |
| `controller` | HTTP 接口：Agent、数据源、域、语义层、报表、MCP Server、用户管理、角色权限 |
| `service` | 数据源、用户角色、功能与数据权限、SQL 授权、资产服务；`SqlQueryScope` 与 `SqlExpressionInspector` 检查 SQL 作用域和表达式 |
| `service.semantic` | 语义层 CRUD、同步、关系、合并；`SemanticSyncDiffService` 计算差异，`SemanticSyncApplyService` 事务写入，`RelationWorkspaceService` 组装关系画布 |
| `interceptor` / `model.holder` | JWT 与权限拦截、审计填充、当前用户上下文 |
| `model.po` / `model.dto` / `model.vo` / `model.bo` | 新持久化、请求、响应与内部业务模型；新 PO 继承 `BasePo`，对外 VO 实现 `BaseVo` |
| `model.converter` / `convertor` | MapStruct 与专用转换：数据源、指标提示、JDBC 元数据和 CSV、会话历史、证据等 |
| `mapper` / `resources/mapper` | MyBatis-Plus 持久层；新 Mapper 继承 `AuditableMapper<T>`，复杂 SQL 按需使用 XML |

旧 `entity`、`dto` 和 `convertor` 仍被存量模块使用，迁移尚未全部完成；新增数据模型使用 `model.*` 目录。

## 4. MCP 配置管理

后端提供 `McpServerController`（`/api/mcp-server`），用于保存外部 MCP Server 配置：

- 支持两种注册形态：
  - **STDIO 型**：保存 `command` / `args` / `env`。
  - **HTTP/SSE 型**：保存 `url` / `headers` / `queryParams`。
- `enable` / `disable` 更新配置记录的启停状态。

当前 `AgentService.init()` 只注册 `DataAgentTool` Bean 和 Skill；尚未读取 MCP 配置来启动进程、建立远程连接或注册外部工具。保存和启用配置不会立即扩展 Agent 能力，也未提供将 Data Agent 作为 MCP Server 对外暴露的入口。

## 5. API 概览（常用端点）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录，返回 JWT token |
| GET | `/api/auth/me` | 本人资料、角色与有效权限 |
| POST | `/api/agent/session` | 创建本人会话，返回服务端分配的 ID |
| POST | `/api/agent/chat/stream` | 发起一次流式对话（SSE） |
| GET | `/api/agent/sessions` | 列出会话 |
| GET | `/api/agent/session/{id}/history` | 会话历史 |
| DELETE | `/api/agent/session/{id}` | 清除会话 |
| DELETE | `/api/agent/session` | 清空全部会话，要求独立权限 |
| GET | `/api/agent/session/{id}/debug` | 原始会话消息，要求调试权限 |
| GET | `/api/agent/evidence` | 按会话 / 回答筛选数据证据，支持分页 |
| GET | `/api/sys/permissions` | 权限点目录 |
| GET/POST/PUT/DELETE | `/api/sys/user[/...]` | 用户增删改查、重置密码 |
| GET/POST/PUT/DELETE | `/api/sys/role[/...]` | 角色增删改查、表级白名单、列级黑名单 |
| GET/POST/PUT/DELETE | `/api/mcp-server[/...]` | MCP Server 配置增删改查与状态启停 |
| REST | `/api/datasource`、`/api/domains`、`/api/semantic/*`、`/api/metric`、`/api/reports`、`/api/table-exports` | 数据源、域、语义层、指标口径、报表与表格导出 |

> 前端导航当前收敛为「AI 智能分析」「数据源管理」「语义管理」「资产管理」「系统管理」。「语义管理」包含数据领域、表语义、指标口径与逻辑外键；「资产管理」包含报告管理、表格导出与数据证据（`/asset/evidence`）；「系统管理」包含用户管理与角色管理。入口和按钮按权限显示。旧路由 `/metric`、`/report`、`/table-export`、`/sys-user`、`/sys-role` 会重定向到对应新入口。

普通 JSON 响应外层使用 `code`、`errorCode`、`message`、`data`。新接口直接返回 `BaseVo` 实现，由 `BaseVoResponseAdvice` 包装为 `ResultVo`；存量接口仍有 `Result<T>`。`ListVo` 通过 `@JsonValue` 保持 `data` 为数组；`BooleanVo` 的 `data` 为 `{ "value": true }`，不能一律当作布尔值解析。分页数据包含 `page`、`pageSize`、`total`、`totalPages`、`hasPrevious`、`hasNext`、`items`；SSE 与文件下载按各自协议处理。

更完整的字段与请求/响应结构见后端 `controller`、`model.dto`、`model.vo` 及存量 `dto` 包源码。

## 6. 可观测性（OpenTelemetry）

后端通过 OpenTelemetry 把一次请求的完整链路串起来：

```
HTTP server span
  └─ invoke_agent
       ├─ chat <model>          # LLM 调用
       ├─ execute_tool <name>   # 工具调用
       │    └─ db.* span        # MyBatis / 动态数据源 SQL
       └─ ...
```

- **同一 traceId**：入口把 OTel context 种入 Reactor context，agentscope 的 `TelemetryTracer` 在异步线程继续沿用，HTTP、Agent、LLM、工具与 SQL 共享一条 trace。
- **JDBC span**：主库与动态数据源都用 `JdbcTelemetry` 包装，SQL 调用产出 `db.*` span；Hikari 池指标通过解包仍可用。
- **日志与指标**：Logback `OTEL` appender 让日志携带 trace/span；Micrometer 指标经 OTLP 导出。
- **默认行为**：只写终端，trace/metrics/logs 导出默认关闭，由环境变量控制。
- **后端无关**：导出是标准 OTLP/HTTP，可对接 Jaeger、Tempo、SigNoz、Uptrace、Honeycomb、Langfuse 等任意 OTel 后端；仓库内的 Grafana LGTM 只是本地示例。

本地查看与配置说明见 [observability.md](observability.md)。

## 7. Agent 内置工具

| 工具 | 用途与边界 |
| --- | --- |
| `get_datasources` | 列出本会话允许使用的数据源；多数据源时先调用它 |
| `get_domains` / `get_tables` / `get_table_schema` | 读取指定数据源的领域、表与字段语义，按当前用户的数据范围过滤；多数据源时必须传 `datasource_id` |
| `get_metric_caliber` | 检索指定数据源的指标口径与业务属性；多数据源时必须传 `datasource_id` |
| `execute_sql` | 仅执行通过校验与授权的 SELECT；最多返回 200 行，超时 30 秒；无 LIMIT/FETCH 时包装查询探测第 201 行，判断截断；多数据源时必须传 `datasource_id` |
| `execute_python` | 调用后端环境的 `python3 -I`，代码内联数据并打印结果；30 秒超时，单实例最多 5 个并发执行 |
| `export_table` | 授权后导出 CSV；最多 10000 行，超限报错，查询超时 120 秒；多数据源时必须传 `datasource_id` |
| `generate_report` | 保存 Markdown 报告，供报告资产页查看 |
| `ask_user` | 挂起并等待用户澄清，前端以 `toolResults` 继续会话 |
| `get_date_info` | 日期、星期、节假日、调休与日期差查询 |

JDBC 连接与元数据读取使用标准接口，但 SQL 仍受解析器、授权支持范围和目标数据库方言约束；自动追加的 LIMIT 也需要目标库支持。Python 直接运行在后端主机的子进程中，当前 Docker 镜像未安装该运行环境，按需配置后使用。

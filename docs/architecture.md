# 架构

本文说明 Data Agent 的系统组成、一次查询请求是如何流转的，以及主要模块与对外接口。"

## 1. 系统组成

| 组件 | 技术 | 职责 |
| --- | --- | --- |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus | 聊天界面、数据源/语义层/报表管理后台、用户与角色管理 |
| 后端 | Spring Boot 4（Java 17+） | Agent 推理、工具执行、语义层、数据源、会话、MCP 管理 |
| Skill 系统 | 多源加载（文件系统 / Git / Nacos / classpath） | 加载"已验证查询模式"作为可复用流程 |
| 元数据库 | MySQL | 存语义层、数据源、会话、MCP 配置、报表、用户、角色与权限等 |
| 查询数据源 | MySQL / PostgreSQL / Oracle | 用户真正要查的业务库，由 Agent 动态连接 |

> 后端包名为 `io.github.malonetalk.agent`，应用名 `data-agent-management`，默认端口 `8080`。

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
                   get_tables/   │  │(MySQL/PG/Oracle)│
                   get_table_    │  └──────────────┘
                   schema        │
                            │     │ 反问用户(ask_user)
                            ▼     ▼
                    ⑤ 流式返回 (Server-Sent-Event) ─────► 前端逐字渲染
```

要点：

- 前端通过 `POST /api/agent/chat/stream` 以 **SSE（Server-Sent Events）** 接收流式回答。
- 后端 `AgentService` 维护一个 **ReAct 循环**：LLM 决定调用哪个工具 → 工具执行 → 结果回灌 → LLM 继续，直到给出最终答案。
- 工具执行 SQL 时，连接到用户在「数据源管理」中配置的目标库，而非元数据库。
- 语义层（域/表/列/关系/指标口径）在需要时从元数据库按需读取，用于把自然语言映射到物理表与字段，并校正指标口径。

## 3. 后端模块划分（按职责）

| 包 / 模块 | 说明 |
| --- | --- |
| `agent` | Agent 主循环、会话、数据源连接管理、模型工厂与提供商 |
| `agent.models` | LLM 提供商抽象：OpenAI / Ollama / DashScope / Anthropic |
| `agent.tools` | 工具集：执行 SQL、取 schema、取域/表、反问用户、生成报表、标记 |
| `agent.skill` | Skill 多源加载（文件/Git/Nacos/classpath） |
| `controller` | HTTP 接口：Agent、数据源、域、语义层、报表、MCP Server、用户管理、角色权限 |
| `service.semantic` | 语义层 CRUD、同步、关系、合并等 |
| `interceptor` | 认证拦截器（JWT）、UserContext |
| `mapper` / `entity` | MyBatis 持久层与实体 |

## 4. MCP 集成

后端提供 `McpServerController`（`/api/mcp-server`），可**注册并管理外部 MCP Server**：

- 支持两种注册形态：
  - **STDIO 型**：配置 `command` / `args` / `env`，由后端拉起本地进程。
  - **HTTP/SSE 型**：配置 `url` / `headers` / `queryParams`，连接远程 MCP 服务。
- 注册后可 `enable` / `disable`，把外部工具纳入 Agent 的工具箱，从而扩展查询与处理能力。

> 当前代码主要体现 Data Agent 作为 **MCP 客户端**去连接外部 Server。若需把 Data Agent 自身能力以 MCP 协议暴露给别的客户端，请关注后续版本或提出 Issue。

## 5. API 概览（常用端点）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录，返回 JWT token |
| POST | `/api/agent/chat/stream` | 发起一次流式对话（SSE） |
| GET | `/api/agent/sessions` | 列出会话 |
| GET | `/api/agent/session/{id}/history` | 会话历史 |
| DELETE | `/api/agent/session/{id}` | 清除会话 |
| GET/POST/PUT/DELETE | `/api/sys/user[/...]` | 用户增删改查、重置密码 |
| GET/POST/PUT/DELETE | `/api/sys/role[/...]` | 角色增删改查、表级白名单、列级黑名单 |
| GET/POST/PUT/DELETE | `/api/mcp-server[/...]` | MCP Server 增删改查与启用/停用 |
| REST | `/api/datasource`、`/api/domain`、`/api/semantic/*`、`/api/report` | 数据源、域、语义层、报表管理 |

> 更完整的字段与请求/响应结构，建议直接阅读后端 `controller` 与 `dto` 包源码。

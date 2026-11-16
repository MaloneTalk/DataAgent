# 外部 Agent 接入（REST + PAT + CLI）

Data Agent 支持外部 Agent 通过 PAT 调用一组只读数据工具，由调用方自行完成推理和编排。本文说明 PAT 鉴权、`/agent/**` REST 接口、`data-agent-cli` 和配套 Skill 的配置与使用。

## 1. 接入组成

| 能力 | 说明 |
| --- | --- |
| 个人访问令牌（PAT） | 用户自助创建、查看、吊销长期 Token；数据库只保存 SHA-256 哈希 |
| `access:pat:manage` 权限 | 控制个人设置中的 PAT 管理页面和接口 |
| `/agent/**` REST 接口 | 使用 PAT 鉴权，与 `/api/**` 的 JWT 会话完全隔离 |
| 工具 REST 暴露 | 通过 `@RestTool` 把指定 `@Tool` 方法暴露为 REST 工具 |
| `data-agent-cli` | npm CLI，负责登录、工具发现、工具调用、session 和 trace |
| `data-agent-skill` | 可直接复制到外部 Agent Skill 目录的问数指南 |
| CLI CI | 独立的 lint、format、build、test 工作流 |

当前暴露的只读工具：

```text
get_datasources
get_domains
get_tables
get_table_schema
get_metric_caliber
execute_sql
```

`execute_sql` 只允许 SELECT；最多返回 200 行，超时 30 秒。

## 2. 接入架构

外部 Agent 通过 CLI 或直接发送 HTTP 请求访问 `/agent/**`：

```text
外部 Agent / CLI
    -> data-agent-cli
    -> /agent/tools/{name}
    -> AgentToolController
    -> AgentToolInvoker
    -> 现有 Tool Bean
    -> PermissionService + DataPermissionService + SqlExecutor
```

主要组件：

| 组件 | 作用 |
| --- | --- |
| `user_pat` / `/api/auth/pats` | PAT 创建、列表和吊销，仅保存哈希 |
| `AgentAuthInterceptor` | 校验 `/agent/**` 的 PAT，加载用户并检查启用状态 |
| `AgentToolController` | 提供 `/agent/health`、`/agent/tools`、`/agent/tools/{name}` |
| `DataAgentTool.restToolMethods()` + `@RestTool` | 声明哪些 `@Tool` 方法同时暴露为 REST 工具 |
| `AgentToolInvoker` | 直接反射调用 Tool Bean，返回 `ToolResultBo` |
| `data-agent-cli` | 登录、工具发现、工具调用、session 和 trace 管理 |
| `data-agent-skill` | 可复制到外部 Agent Skill 目录的问数指南 |

## 3. 数据库与权限准备

已有数据库需要执行迁移：

```bash
mysql -u root -p data_agent < sql/migration.sql
```

执行后在「系统管理 → 角色管理」中给需要自助创建 PAT 的角色授予：

```text
access:pat:manage 访问令牌：管理
```

超级管理员默认拥有该权限，但仍需要给普通角色显式授权。

`access:pat:manage` 只允许管理令牌。读取数据源和语义还需 `data:schema:read`，查询需 `analysis:sql:execute`，指标口径需 `semantic:metric:read`，并配置对应数据源下的表或视图及可见列。仅调用外部只读工具不要求 `analysis:chat:use`。权限在执行时重新检查；`/agent/health` 和工具目录可访问不代表数据查询一定可执行。

## 4. 创建 PAT

登录 Web 后进入「API 访问令牌」页面（`/settings/tokens`）：

1. 点击「新建令牌」。
2. 填写名称，选择有效期，默认 90 天。
3. 创建成功后立即复制 Token。Token 只显示一次。

每个用户最多保留 10 个未吊销且未过期的 PAT。

有效期允许在创建后的 1–365 天之间，省略时为 90 天。令牌到期、被吊销或用户停用后不能继续使用。

## 5. 安装和使用 CLI

CLI 需要 Node.js 20 或更高版本。

发布后全局安装：

```bash
npm install -g @malone-talk/data-agent-cli
```

本地开发时也可以在仓库内构建并链接：

```bash
cd data-agent-cli
npm ci
npm run build
npm link
```

登录并保存配置：

```bash
data-agent-cli auth login
```

也可以直接传入 Token：

```bash
data-agent-cli auth login --token pat_xxx
```

默认 Base URL 是 `http://localhost:8080`。服务部署在其他地址时：

```bash
data-agent-cli auth login \
  --base-url https://dataagent.example.com \
  --token pat_xxx
```

检查登录状态：

```bash
data-agent-cli auth status
```

查看可用工具和参数定义：

```bash
data-agent-cli tools list
data-agent-cli tools describe get_tables
```

调用工具：

```bash
data-agent-cli tools call get_datasources --input '{}'

data-agent-cli tools call get_domains \
  --input '{"datasource_id":1}'

data-agent-cli tools call get_tables \
  --input '{"datasource_id":1,"domains":["sales"]}'

data-agent-cli tools call execute_sql \
  --input '{"datasource_id":1,"sql":"SELECT ..."}'
```

Agent 场景建议使用 `--json`，或确保 stdout 不是 TTY。stdout 输出数据或错误 JSON，stderr 输出日志。

### 直接调用 REST

不使用 CLI 时，可直接发送 Bearer PAT；Base URL 使用服务根地址，不要追加 `/api`：

```bash
export DATAAGENT_TOKEN="pat_替换为实际令牌"

curl -H "Authorization: Bearer ${DATAAGENT_TOKEN}" \
  http://localhost:8080/agent/tools

curl -X POST http://localhost:8080/agent/tools/get_tables \
  -H "Authorization: Bearer ${DATAAGENT_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Session-Id: run-123" \
  -d '{"datasource_id":1,"domains":["sales"]}'
```

接口返回统一 JSON 外层，工具调用的 `data` 包含 `text`、`metadata` 和 `traceId`。工具内部失败可能仍以 HTTP 200 返回，此时 `data.metadata.errorCode` 和 `message` 表示失败，不能只检查 HTTP 状态或外层 `code`。CLI 会识别该类结果并以退出码 `5` 结束。

当前没有 `GET /agent/tools/{name}` 详情接口；CLI 的 `tools describe` 从 `GET /agent/tools` 的目录中筛选工具和参数定义。

## 6. 安装 Skill

`data-agent-skill/` 是可直接复制到外部 Agent Skill 存储目录的文件夹，其中 `SKILL.md` 包含：

- CLI 安装与 PAT 配置
- 适用场景
- 基本使用方法
- 推荐问数流程
- 如何通过 `data-agent-cli skill` 获取更详细说明
- session、输出和退出码约定

复制方式：

```bash
cp -R data-agent-skill <your-agent-skill-directory>/
```

不同 Agent 的 Skill 目录不同，请参考对应 Agent 的文档。复制后 Agent 可以通过 `data-agent-cli skill` 获取完整指南，通过 `data-agent-cli tools list` 和 `data-agent-cli tools describe` 获取当前工具定义。

## 7. Session 与 Trace

CLI 会自动发送 W3C `traceparent`。同一 session 在 30 分钟空闲窗口内复用同一个 traceId。

需要多次调用属于同一业务会话时传入：

```bash
data-agent-cli tools call get_tables \
  --session-id run-123 \
  --input '{"datasource_id":1,"domains":["sales"]}'
```

也可以设置 `DATAAGENT_SESSION_ID`。未提供时后端使用 `__default__` 默认会话。

CLI 将该值放入 `X-Session-Id`，后端生成 `CLIENT_<userId>_<外部ID>` 命名空间用于工具调用上下文。该标识用于外部调用分组，不创建 Web 聊天历史、不运行内置 ReAct 循环，也不与 `AGENTSCOPE_` 会话互通。调用方自行保存上下文和编排工具；多数据源时显式传 `datasource_id`。

## 8. 配置

配置路径：

```text
Linux/macOS: ${XDG_CONFIG_HOME:-~/.config}/data-agent-cli/config.json
Windows:     %APPDATA%\data-agent-cli\config.json
```

示例：

```json
{
  "baseUrl": "http://localhost:8080",
  "token": "pat_xxx",
  "timeoutSeconds": 60
}
```

Token 优先级：`--token` > 配置文件 > `DATAAGENT_TOKEN`。

Base URL 的解析顺序为 `--base-url` > 配置文件 > `DATAAGENT_BASE_URL` > 默认地址；当前配置加载器会填入默认地址，因此部署到其他地址时建议通过 `auth login --base-url ...` 显式保存。`auth login` 的 Token 来源为 `--token`、`DATAAGENT_TOKEN` 或交互输入，不复用已存 Token。默认请求超时 60 秒，可用 `--timeout` 覆盖。

更多命令和退出码说明见 [data-agent-cli/README.md](../data-agent-cli/README.md)。

## 9. 安全与边界

- PAT 只能访问 `/agent/**`，不能访问 `/api/**` 用户接口。
- 工具内部仍会重新检查功能权限、数据源权限、表白名单和隐藏列。
- CLI 不保存明文 Token 到日志或错误信息。
- 配置文件以 0600 权限创建；Windows 的权限语义不同，应通过用户目录的访问控制保护本地配置。
- 当前不提供 MCP、Python 执行、图表生成、表格导出和报告生成；内置聊天工具不等于 REST 暴露工具。
- `execute_sql` 只允许 SELECT，不替代数据库自身的只读账号约束。

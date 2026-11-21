# data-agent-cli

通过 PAT 调用 DataAgent 只读工具的命令行客户端。

需要 Node.js 20 或更高版本。

## 安装

```bash
npm install -g @malone-talk/data-agent-cli
```

## 创建 PAT

在浏览器中打开 DataAgent，进入 **API 访问令牌** 页面（`/settings/tokens`），创建令牌并复制。Token 只会显示一次。

## 登录

```bash
data-agent-cli auth login
```

也可以直接传入 Token：

```bash
data-agent-cli auth login --token pat_xxx
```

`auth login` 会调用 `GET /agent/health` 校验 Token，成功后把 Token 和 Base URL 保存到本地配置。

```bash
data-agent-cli auth status
data-agent-cli auth logout
```

默认 Base URL 是 `http://localhost:8080`。

## 工具

```bash
data-agent-cli tools list
data-agent-cli tools describe get_tables
```

使用 JSON 输入调用工具：

```bash
data-agent-cli tools call get_domains --input '{"datasource_id":1}'

data-agent-cli tools call get_tables \
  --input '{"datasource_id":1,"domains":["sales"]}' \
  --json
```

`--input` 也支持文件或 stdin：

```bash
data-agent-cli tools call execute_sql --input @query.json
printf '{"datasource_id":1}' | data-agent-cli tools call get_domains --input -
```

`execute_sql` 只支持 SELECT。查询结果最多返回 200 行，需要更多数据时使用 `LIMIT/OFFSET` 分页。

当前 REST 工具为 `get_datasources`、`get_domains`、`get_tables`、`get_table_schema`、`get_metric_caliber` 和 `execute_sql`。多数据源时显式传 `datasource_id`；表与普通视图共用语义和授权。`generate_chart` 等内置聊天工具不对 CLI 暴露。

创建 PAT 需要 `access:pat:manage`，工具执行仍要求所属用户具备 schema、SQL 或指标权限及目标表列授权。完整权限及直接 REST 调用说明见[外部接入指南](../docs/external-agent.md)。

## Agent 指南

输出面向外部 Agent 的 Markdown 使用指南：

```bash
data-agent-cli skill
```

指南包含认证检查、工具发现、推荐问数流程、session 处理以及输出与错误约定。

## Session 与 Trace

通过 `--session-id` 让多次调用属于同一个业务会话：

```bash
data-agent-cli tools call get_tables \
  --session-id run-123 \
  --input '{"datasource_id":1,"domains":["sales"]}'
```

也可以设置 `DATAAGENT_SESSION_ID`。

CLI 会自动发送 W3C `traceparent`。同一 session 在 30 分钟空闲窗口内复用同一个 traceId。

`--session-id` 作为 `X-Session-Id` 发送，用于外部调用上下文及 trace 分组；它不会创建 Web 聊天会话，也不保存或自动续接模型推理历史。

## 配置

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

环境变量：

```text
DATAAGENT_TOKEN
DATAAGENT_BASE_URL
DATAAGENT_SESSION_ID
TRACEPARENT
```

Token 优先级：`--token` > 配置文件 > `DATAAGENT_TOKEN`。

`auth login` 使用 `--token`、`DATAAGENT_TOKEN` 或交互输入；不会复用配置中已有 Token。部署到其他地址时使用 `auth login --base-url https://dataagent.example.com --token pat_xxx` 保存服务根地址，不追加 `/api`。配置加载器会填入默认 Base URL，因此建议显式保存地址，不依赖 `DATAAGENT_BASE_URL` 覆盖已加载配置。

## 输出

- 交互式终端：输出人类可读文本或表格
- `--json` 或 stdout 非 TTY：输出 JSON
- `--json` 模式下错误也以 JSON 输出到 stdout
- 日志、进度、警告和 `--verbose` 详情输出到 stderr
- 工具内部失败也可能收到 HTTP 200；CLI 检查结果中的 `metadata.errorCode`，并以退出码 `5` 结束

退出码：

```text
0  成功
2  参数、输入或配置错误
3  认证或授权失败
4  网络错误或超时
5  服务端错误或工具执行失败
```

## 开发

```bash
npm ci
npm run lint:check
npm run format:check
npm run build
npm test
```

## 指标精确查询与澄清

已知稳定 key 时调用 `get_metric_caliber`，输入例如 `{"datasource_id":1,"metric_key":"paid_sales"}`；未知时用 `hint` 搜索名称或别名。检查返回元数据 `lookupStatus`，只有 `RESOLVED` 才能使用完整口径。

`AMBIGUOUS` 且 `requiresClarification=true` 时先展示候选的 key 与口径差异，请用户选择，不能默认采用第一名。确认前服务端阻止该会话继续执行数据操作。外部应用获得用户明确选择后，使用同一会话调用：

```bash
data-agent-cli tools confirm-metric paid_sales --session-id analysis-1
data-agent-cli tools call get_metric_caliber --session-id analysis-1 --input '{"datasource_id":1,"metric_key":"paid_sales"}'
```

REST 确认入口为 `POST /agent/metric-choice`，请求体 `{"metricKey":"paid_sales"}`，必须沿用同一 `X-Session-Id`。返回 `{"value":false}` 表示候选已变化或删除，需重新查询并请用户再次选择；返回 `{"value":true}` 后再按所选 key 读取完整口径。确认不是模型工具；不能把模型自行选择视为用户确认。`requiresClarification=false` 表示多个独立指标，应拆分查找，不要求用户二选一。`NOT_FOUND` 时请用户补充名称或 key，不能自动采用建议项。

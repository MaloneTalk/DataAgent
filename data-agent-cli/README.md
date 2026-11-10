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

## 输出

- 交互式终端：输出人类可读文本或表格
- `--json` 或 stdout 非 TTY：输出 JSON
- `--json` 模式下错误也以 JSON 输出到 stdout
- 日志、进度、警告和 `--verbose` 详情输出到 stderr

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

构建产物输出到 `dist/`。npm 包由维护者手动发布。

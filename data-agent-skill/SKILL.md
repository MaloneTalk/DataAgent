---
name: data-query
description: A skill for querying database tables and generating SQL queries based on table schemas.
---

# DataAgent CLI 问数 Skill

本 Skill 用于指导 Agent 通过 `data-agent-cli` 调用 DataAgent 的只读数据工具，
完成数据源发现、语义表查询、表结构查看、指标口径查询和 SELECT 查询。

## 适用场景

- 用户想用自然语言查询业务数据库
- 需要先了解有哪些数据源、领域、表、字段或指标口径
- 需要根据表结构生成 SQL
- 需要执行只读 SELECT 并总结结果
- 需要把 DataAgent 能力接入 Claude Code、Cursor 等外部 Agent

不适用于：

- INSERT / UPDATE / DELETE 等写操作
- 导出文件、生成报告、执行 Python
- 需要人工追问的场景（由外部 Agent 自己处理）

## 安装 CLI

需要 Node.js 20 或更高版本：

```bash
npm install -g @malone-talk/data-agent-cli
```

## 配置

1. 在浏览器打开 DataAgent，进入 `/settings/tokens` 创建 PAT。
2. 登录 CLI：

```bash
data-agent-cli auth login
```

也可以直接传入 Token：

```bash
data-agent-cli auth login --token pat_xxx
```

默认 Base URL 是 `http://localhost:8080`。如果服务部署在其他地址：

```bash
data-agent-cli auth login \
  --base-url https://dataagent.example.com \
  --token pat_xxx
```

检查登录状态：

```bash
data-agent-cli auth status
```

Token 优先级为：`--token` > 配置文件 > `DATAAGENT_TOKEN`。

## 基本使用方法

查看可用工具：

```bash
data-agent-cli tools list
```

查看某个工具的参数定义：

```bash
data-agent-cli tools describe get_tables
```

调用工具：

```bash
data-agent-cli tools call get_datasources --input '{}'

data-agent-cli tools call get_domains \
  --input '{"datasource_id":1}'

data-agent-cli tools call get_tables \
  --input '{"datasource_id":1,"domains":["sales"]}'

data-agent-cli tools call get_table_schema \
  --input '{"datasource_id":1,"table_name":"orders"}'

data-agent-cli tools call execute_sql \
  --input '{"datasource_id":1,"sql":"SELECT ..."}'
```

推荐流程：

1. `get_datasources`
2. `get_domains`
3. `get_tables`
4. `get_table_schema`
5. 指标场景先调用 `get_metric_caliber`
6. `execute_sql`
7. 根据结果总结答案

## 获取更详细的说明

执行以下命令获取完整的 Agent 使用指南：

```bash
data-agent-cli skill
```

该命令不需要登录，也不需要访问网络，会输出 Markdown 指南，内容包括：

- 认证检查
- 工具发现和推荐调用顺序
- 参数规则
- session 规则
- 输出与错误处理
- 完整示例

当不确定工具名、参数格式或问数流程时，优先执行：

```bash
data-agent-cli skill
data-agent-cli tools list
data-agent-cli tools describe <tool-name>
```

不要猜测工具名或参数。

## Session 与输出

多次调用需要属于同一业务会话时，传入 `--session-id`：

```bash
data-agent-cli tools call get_tables \
  --session-id run-123 \
  --input '{"datasource_id":1,"domains":["sales"]}'
```

也可以设置 `DATAAGENT_SESSION_ID`。

Agent 调用时建议使用 `--json`，或确保 stdout 不是 TTY。stdout 输出数据或错误 JSON，stderr 输出日志。

退出码：

```text
0  成功
2  参数、输入或配置错误
3  认证失败
4  网络错误或超时
5  服务端错误或工具执行失败
```

工具执行失败时可能仍返回 HTTP 200，需要检查 `metadata.errorCode`。

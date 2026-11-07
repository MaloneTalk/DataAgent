# Data Agent 文档导航

欢迎阅读 Data Agent 文档。本文档集按"从入门到深入"组织，建议第一次接触本项目从 [getting-started.md](getting-started.md) 开始。

## 必读顺序

| # | 文档 | 适合谁看 | 内容 |
| --- | --- | --- | --- |
| 1 | [getting-started.md](getting-started.md) | 想跑起来的人 | 环境准备、建库、启动前后端、第一次提问 |
| 2 | [architecture.md](architecture.md) | 想理解系统的人 | 系统组成、会话协议、请求流转、数据证据、模块划分与 API |
| 3 | [configuration.md](configuration.md) | 要部署/接入的人 | LLM 提供商、元数据库、查询数据源、Skill、MCP 配置、安全提示 |
| 4 | [semantic-layer.md](semantic-layer.md) | 要维护业务知识的人 | 语义层概念、管理界面、同步机制、指标业务属性与API示例 |
| 5 | [skills.md](skills.md) | 要扩展 Agent 能力的人 | Skill 编写、工作目录、加载与生效方式 |
| 6 | [observability.md](observability.md) | 要观测/排障的人 | OpenTelemetry trace/metrics/logs、配置开关、任意 OTLP 后端（Grafana LGTM 示例） |
| 7 | [permissions.md](permissions.md) | 要配置权限的人 | 权限点、角色授权、数据范围与数据库升级 |
| 8 | [csv-import.md](csv-import.md) | 要导入文件的人 | CSV 导入库配置、字段类型、任务恢复与权限 |

## 贡献相关

| 文档 | 说明 |
| --- | --- |
| [contributing.md](contributing.md) | 开发环境搭建、分支与 PR 流程、代码规范、提交信息约定、CI |

## 仓库布局

```
DataAgent/
├── README.md                 # 仓库首页（本仓库根目录）
├── LICENSE                   # AGPL-3.0
├── AGENTS.md                 # 后端工程原则（代码规范的总纲）
├── data-agent-backend/       # Spring Boot 后端
├── data-agent-frontend/      # Vue 3 + TS + Vite 前端
├── docker/                   # Docker Compose（含 observability/ 观测栈）
├── skills/                   # Skill 目录（内置 + 自定义，启动时加载）
├── sql/                      # 主文件 data_source.sql + 迁移文件 migration.sql
└── docs/                     # 本文档集
```

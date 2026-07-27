# Data Agent 文档导航

欢迎阅读 Data Agent 文档。本文档集按"从入门到深入"组织，建议第一次接触本项目从 [getting-started.md](getting-started.md) 开始。

## 必读顺序

| # | 文档 | 适合谁看 | 内容 |
| --- | --- | --- | --- |
| 1 | [getting-started.md](getting-started.md) | 想跑起来的人 | 环境准备、建库、启动前后端、第一次提问 |
| 2 | [architecture.md](architecture.md) | 想理解系统的人 | 系统组成、请求流转、模块划分、MCP 集成、API 概览 |
| 3 | [configuration.md](configuration.md) | 要部署/接入的人 | LLM 提供商、元数据库、查询数据源、Skill、MCP 配置、安全提示 |
| 4 | [semantic-layer.md](semantic-layer.md) | 要维护业务知识的人 | 语义层概念、管理界面操作、同步机制 |

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
├── skills/                   # 内置 Skill（如 data-query）
├── sql/                      # 元数据库初始化脚本（data_source.sql）
├── io/agentscope/            # 实验性/参考代码
└── docs/                     # 本文档集
```

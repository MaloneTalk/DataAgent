# Data Agent

> An LLM-powered data query agent that maps business language to data through a semantic layer.

**Data Agent** 是一个面向业务人员的自然语言数据查询智能体：你用中文提问，它把问题理解成 SQL、在目标数据库上执行，并以流式对话的方式把结果与分析返回给你。它内置**语义层**，把"业务语言"映射到"数据表示"，让 LLM 真正"懂业务"。

[![Backend CI](https://github.com/MaloneTalk/DataAgent/actions/workflows/backend.yml/badge.svg)](https://github.com/MaloneTalk/DataAgent/actions/workflows/backend.yml)
[![Frontend CI](https://github.com/MaloneTalk/DataAgent/actions/workflows/frontend.yml/badge.svg)](https://github.com/MaloneTalk/DataAgent/actions/workflows/frontend.yml)
[![License: AGPL v3](https://img.shields.io/github/license/MaloneTalk/DataAgent)](LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/MaloneTalk/DataAgent)

> **项目状态**：由于开发时间有限，项目仍在持续打磨中，部分功能与文档可能尚未完善。开发者会持续迭代，同时也非常期待社区的参与——欢迎提交 Issue 与 PR。

## ✨ 特性

- **自然语言查数**：基于 LLM + ReAct 工具调用，把自然语言转为 SQL 并在目标库执行，全程流式输出。
- **多模型可切换**：内置 OpenAI / Ollama / 通义 DashScope / Anthropic 等提供商，换底座模型不影响已沉淀的业务知识。
- **多数据源**：支持查询 MySQL、PostgreSQL、Oracle。
- **语义层**：域（Domain）/ 逻辑表 / 逻辑列 / 表关系 / 指标口径的业务映射，让 LLM 真正"懂业务"。
- **会话式分析**：SSE 流式回答，会话历史可追溯、可调试。
- **报表生成**：内置报表工具与配套前端报表视图。
- **Skill 系统**：从文件系统 / Git / Nacos 多源加载可复用的查询流程（"已验证查询模式"）。
- **MCP 集成**：可注册并连接外部 MCP Server，把外部工具纳入 Agent 工具箱。

## 🏗️ 架构速览

```
用户 ──► 前端(Vue3) ──► POST /api/agent/chat/stream (SSE)
                            │
                            ▼
                     AgentService (ReAct 循环)
       ┌───────────────┴───────────────┬──────────────────┐
   LLM(可切换底座)              工具集(SQL/Schema/反问/报表)      语义层 (MySQL)
                            │
                            ▼
                     目标数据源 (MySQL / PG / Oracle)
```

- **元数据库**（存放语义层、数据源、会话、MCP 配置等）使用 MySQL。
- **被查询的业务库**可以是 MySQL / PostgreSQL / Oracle。

完整架构与请求流转见 [docs/architecture.md](docs/architecture.md)。

## 🚀 快速开始

> 完整、带截图的步骤见 [docs/getting-started.md](docs/getting-started.md)。

**前置依赖**：JDK 17+、Maven 3.9+、Node 18+、pnpm 8+、MySQL 8+。

```bash
# 1. 建元数据库并初始化表结构
mysql -u root -p -e "CREATE DATABASE data_agent CHARACTER SET utf8mb4;"
mysql -u root -p data_agent < sql/data_source.sql

# 2. 启动后端（默认端口 8080）
cd data-agent-backend
mvn spring-boot:run

# 3. 启动前端（默认端口 3000）
cd data-agent-frontend
pnpm install && pnpm dev
```

浏览器打开 http://localhost:3000 ，在聊天框用自然语言提问，例如：*"上个月各区域销售额是多少？"*

## 📚 文档

| 文档 | 说明 |
| --- | --- |
| [docs/README.md](docs/README.md) | 文档导航索引 |
| [docs/architecture.md](docs/architecture.md) | 整体架构与请求流转 |
| [docs/getting-started.md](docs/getting-started.md) | 详细安装与启动 |
| [docs/configuration.md](docs/configuration.md) | LLM、数据源、Skill、MCP 配置 |
| [docs/semantic-layer.md](docs/semantic-layer.md) | 语义层概念与管理 |
| [docs/contributing.md](docs/contributing.md) | 开发环境与贡献流程 |

## 🤝 贡献

由于时间关系，项目仍在持续完善中，开发者深知其中尚有诸多不完善之处，会继续努力迭代。同时也真诚欢迎社区的参与：提交 Issue 反馈问题与建议、补充文档、修复 Bug、实现新特性……任何形式的贡献都欢迎。开发规范与提交流程见 [docs/contributing.md](docs/contributing.md)。

## 📄 许可证

本项目基于 [AGPL-3.0](LICENSE) 开源。


# Data Agent

> An LLM-powered data query agent that maps business language to data through a semantic layer.

**Data Agent** 是一个面向业务人员的自然语言数据分析智能体：你用中文提问，它把问题理解成 SQL 或 Python 分析代码、执行查询与统计计算，并以流式对话的方式把结果与分析返回给你。它内置**语义层**，把"业务语言"映射到"数据表示"，让 LLM 真正"懂业务"。

[![Backend CI](https://github.com/MaloneTalk/DataAgent/actions/workflows/backend.yml/badge.svg)](https://github.com/MaloneTalk/DataAgent/actions/workflows/backend.yml)
[![Frontend CI](https://github.com/MaloneTalk/DataAgent/actions/workflows/frontend.yml/badge.svg)](https://github.com/MaloneTalk/DataAgent/actions/workflows/frontend.yml)
[![License: AGPL v3](https://img.shields.io/github/license/MaloneTalk/DataAgent)](LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/MaloneTalk/DataAgent)

> **项目状态**：由于开发时间有限，项目仍在持续打磨中，部分功能与文档可能尚未完善。开发者会持续迭代，同时也非常期待社区的参与——欢迎提交 Issue 与 PR。

## ✨ 特性

- **自然语言查数**：基于 LLM + ReAct 工具调用，把自然语言转为 SQL 并在目标库执行，全程流式输出。
- **Python 数据分析**：对查询结果自动执行统计分析（相关性、回归、分布检验），补齐 SQL 在复杂统计计算上的短板。
- **多模型可切换**：内置 OpenAI 兼容接口 / Ollama / 通义 DashScope / Anthropic 等提供商，换底座模型不影响已沉淀的业务知识。
- **多数据源（JDBC 抽象）**：数据读取与执行完全基于 JDBC 标准 API，因此支持**任意 JDBC 兼容数据库**——内置类型已覆盖 MySQL / PostgreSQL / Oracle（已验证）与 ClickHouse / SQL Server / 达梦 / OceanBase / SQLite，其余 JDBC 兼容库扩展枚举即可接入。

  > **默认仅内置 MySQL 驱动**。使用其他数据库前，需先在 `data-agent-backend/pom.xml` 中添加对应 JDBC 驱动依赖并重新构建后端，否则新增数据源时会提示「未找到数据库驱动」。类型列表与 Maven 坐标见 [docs/configuration.md](docs/configuration.md#4-查询数据源)。
- **语义层（无向量召回）**：以"域（Domain）"组织表，由 LLM 在工具调用时**推理出业务问题所属域、主动选表**，而非向量相似度召回——更精准、更稳定，也无需维护任何 embedding 索引。维度包括逻辑表 / 逻辑列 / 表关系 / 指标口径的业务映射。
- **会话式分析**：SSE 流式回答，会话历史可追溯、可调试。
- **报表生成**：内置报表工具与配套前端报表视图。
- **Skill 系统**：从文件系统 / Git / Nacos 多源加载可复用的查询流程（"已验证查询模式"）。
- **MCP 集成（开发中）**：可注册并连接外部 MCP Server，把外部工具纳入 Agent 工具箱。当前能力仍在打磨，欢迎参与共建。

## 💡 核心设计亮点

### 1. 语义层召回：用模型推理替代向量检索

传统 Text-to-SQL 方案通常用向量相似度从成百上千张表里"召回"候选表，但向量召回既容易误召回 / 漏召回，又要额外维护 embedding 模型与向量索引。

Data Agent 反其道而行——**不引入任何向量检索**。语义层把表组织成"域（Domain）"，LLM 在工具调用时直接**推理出业务问题属于哪个域**，再由 `get_tables` 工具按域精确返回该域下的表（`GetTablesTool` → `TableSemanticService.listMergedTablesByDomains`）。

理由很直接：判断"上个月各区域销售额"属于"销售域"这件事，让模型去理解语义，比让向量去算余弦相似度**更准、更稳**，而且零额外依赖、零索引维护成本。

### 2. 数据源层：纯 JDBC 抽象，兼容一切关系型库

- `SchemaReader` 完全基于 JDBC 标准 `DatabaseMetaData` 读取表 / 列 / 主键，不绑定任何数据库方言；
- `SqlExecutor` 只使用 `Connection` / `PreparedStatement` / `ResultSet` 执行查询，并叠加 SELECT 校验、自动 `LIMIT` 等安全护栏。

整条"读取表结构 → 执行查询"的路径都跑在 JDBC 标准 API 上，因此只要目标库**提供 JDBC 驱动**，Data Agent 就能接入。目前已内置 MySQL / PostgreSQL / Oracle / ClickHouse / SQL Server / 达梦 / OceanBase / SQLite 八种类型（前端数据源下拉与后端枚举同步支持），其余任意 JDBC 兼容数据库扩展 `DataSourceType` 枚举即可接入。注意：**默认发布包仅内置 MySQL 驱动**，接入其他数据库前请先按 [docs/configuration.md](docs/configuration.md#4-查询数据源) 在 `data-agent-backend/pom.xml` 中引入对应驱动。

## 🏗️ 架构速览

```
用户 ──► 前端(Vue3) ──► POST /api/agent/chat/stream (SSE)
                            │
                            ▼
                     AgentService (ReAct 循环)
       ┌───────────────┴───────────────┬──────────────────┐
   LLM(可切换底座)              工具集(SQL/Schema/Python/反问/报表)      语义层 (MySQL)
                            │
                            ▼
                     目标数据源 (任意 JDBC 兼容数据库)
```

- **元数据库**（存放语义层、数据源、会话、MCP 配置等）使用 MySQL。
- **被查询的业务库**可以是任何提供 JDBC 驱动的数据库（MySQL / PostgreSQL / Oracle 已验证，其余 JDBC 兼容库引入驱动即可）。

完整架构与请求流转见 [docs/architecture.md](docs/architecture.md)。

## 🚀 快速开始

> 完整、带截图的步骤见 [docs/getting-started.md](docs/getting-started.md)。

**前置依赖**：JDK 17+、Maven 3.9+、Node 18+、pnpm 8+、MySQL 8+、Python 3+（需安装 pandas、numpy、scipy）。

```bash
# 1. 建元数据库并初始化表结构
mysql -u root -p -e "CREATE DATABASE data_agent CHARACTER SET utf8mb4;"
mysql -u root -p data_agent < sql/data_source.sql

# 2. 配置首启必需环境变量
export ADMIN_INIT_PASSWORD="你的管理员初始密码"
export IO_GITHUB_MALONETALK_MODEL_API_KEY="你的模型 API Key"

# 可选：默认走 OpenAI 兼容接口，application.properties 当前示例指向 DeepSeek
export IO_GITHUB_MALONETALK_MODEL_PROVIDER="openai"
export IO_GITHUB_MALONETALK_MODEL_NAME="deepseek-v4-flash"
export IO_GITHUB_MALONETALK_MODEL_BASE_URL="https://api.deepseek.com"

# 3. 启动后端（默认端口 8080）
cd data-agent-backend
mvn spring-boot:run

# 4. 启动前端（默认端口 3000）
cd data-agent-frontend
pnpm install && pnpm dev
```

浏览器打开 http://localhost:3000 ，使用 `admin` / `ADMIN_INIT_PASSWORD` 登录后，在聊天框用自然语言提问，例如：*"上个月各区域销售额是多少？"*

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

## 💬 交流

钉钉交流群: **154405001431**（"DataAgent用户1群"）

> 部分用户可能因账号安全问题无法加入，条件允许的情况下可换账号申请。

## 📄 许可证

本项目基于 [AGPL-3.0](LICENSE) 开源。


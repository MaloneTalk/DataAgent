# DataAgent 文档导航

生成时间：2026-05-12  
用途：作为 DataAgent 项目的统一文档入口，快速定位核心信息与阅读路径

---

## 一、项目定位

当前 DataAgent 是一个 **具备语义层能力的查数助手后端骨架**，而不是完整的产品化 DataAgent 平台。

### 已具备核心能力
- **Agent 主链路**：`get_tables -> get_table_schema -> execute_sql` 工具编排
- **语义层**：表语义、列语义、关系语义三层，已接入 Agent 消费
- **数据源管理**：动态连接池、物理 schema 读取、只读 SQL 执行
- **会话能力**：同步对话、SSE 流式对话、MySQL Session 持久化
- **工程基线**：Maven 构建、Spotless 校验、GitHub Actions CI

### 当前主要缺口
- Agent 产品化能力（显式 datasource 路由、结构化事件流、异步任务、查询历史）
- 数据源与执行安全（健康检查、密码治理、SQL 校验增强、审计）
- 大数据量处理（数据层分页、schema 缓存、结果缓存）
- 前端交互层（聊天页、语义管理页、数据源管理页、结果可视化）
- 测试与治理（测试基线、审计日志、版本历史、可观测性）

详细现状参见：`context-summary-semantic-service.md`

---

## 二、后端主链路

### 核心入口
- **API 层**：`AgentController` (`POST /api/agent/chat`, `POST /api/agent/chat/stream`)
- **Agent 编排**：`AgentService` (ReActAgent + 三个工具)
- **语义层服务**：`SemanticSchemaService` (表/列/关系语义聚合)
- **SQL 执行**：`SqlExecutor` (只读 SELECT，最多 200 行，超时 30 秒)

### 工具链
1. `GetTablesTool` → 返回可见表分页列表
2. `GetTableSchemaTool` → 返回表结构化 schema (列、关系分页)
3. `ExecuteSqlTool` → 执行 SQL 并返回结果

### 流式输出现状
当前为 `Flux<String>` 文本流，尚未结构化事件分类。

详细架构参见：`../reference/dataagent-architecture-baseline.md`

---

## 三、语义层现状

### 三层语义
- **表语义**：`TableInfo` (表名、描述、domain、可见性)
- **列语义**：`ColumnSemanticInfo` (列名、描述、可见性)
- **关系语义**：`LogicalTableRelation` (source/target 表列、关系类型、启用状态)

### 核心服务
- **可见性与 merge**：`SemanticVisibilitySupport` (物理 schema + 语义 metadata 合并)
- **关系管理**：`LogicalTableRelationServiceImpl` (候选表/列、有效性判定)
- **对 Agent 暴露**：`SemanticSchemaService` (生成 AI 可见的语义 prompt)

### 当前缺失
- 高级字段语义 (display name, semantic type, time role, enum schema)
- 指标语义层 (metric / measure / dimension 独立建模)
- 治理层 (审计日志、版本历史、draft/published 状态)

详细事实参见：`../reference/semantic-layer-baseline.md`

---

## 四、外部对标入口

### 指标与语义层建模
- **dbt Semantic Layer / MetricFlow**：entities、dimensions、measures、metrics 分层
- **Cube**：语义层 + API + 缓存 + 访问控制

### 查询产品化与结果层
- **Apache Superset**：异步查询、结果层、查询产品化
- **Vanna**：NL2SQL + 结果对象 (SQL + DataFrame + 图表 + follow-up questions)

### Agent 主链路与分析体验
- **DB-GPT**：分步查数工具链、工作流编排
- **Lightdash**：dbt 语义资产 + 分析 UI 消费

### 治理与元数据平台
- **OpenMetadata / DataHub**：Glossary、Lineage、审计、版本、ownership

### SQL 执行治理
- **SQLMesh**：SQL 对象化、状态管理、血缘、可验证性

详细对标参见：`../reports/dataagent-industry-benchmarks.md`

---

## 五、推荐阅读路径

### 快速进入项目（15 分钟）
1. **本文档** (`dataagent-doc-map.md`) ← 你在这里
2. `context-summary-semantic-service.md` ← 当前现状摘要
3. `../reference/semantic-layer-baseline.md` ← 代码事实基线

### 完整理解与规划（1-2 小时）
4. `../reference/dataagent-architecture-baseline.md` ← 全局架构事实
5. `../reports/dataagent-overall-gap-report.md` ← 整体缺口分析
6. `../reports/dataagent-master-roadmap.md` ← 总体建设路线图
7. `../reports/dataagent-industry-benchmarks.md` ← 行业对标参考
8. `../planning/phase1-task-list.md` ← Phase 1 任务清单

### 专题深入（按需）
9. `../reports/dataagent-implementation-gap-analysis.md` ← **DataAgent 实施差距分析（代码现状 vs 调研目标）**
10. `../reports/dataagent-agentscope-java-research.md` ← **AgentScope-Java 框架深度适配方案（ToolGroup/AG-UI/Chunk Callback）**
11. `../reports/semantic-layer-implementation-update.md` ← **语义层实施更新报告（结合 AgentScope-Java 验证结果）**
11. `../reports/semantic-layer-implementation-research.md` ← **语义层演进实现方案（高级字段/指标/治理/completeness）**
12. `../reports/dataagent-routing-sse-research.md` ← **多数据源路由 + 结构化 SSE 事件流方案**
13. `../reports/dataagent-security-execution-research.md` ← **SQL 安全/审计日志/连通性测试/密码治理方案**
14. `../reports/dataagent-testing-implementation-research.md` ← **自动化测试体系（分层策略/代码骨架/H2 坑点）**
14. `../reports/dataagent-agent-backend-report.md` ← Agent 主链路调研
15. `../reports/dataagent-datasource-execution-report.md` ← 数据源与执行链调研
16. `../reports/dataagent-platform-readiness-report.md` ← 前端、测试与平台完备度
17. `../reports/semantic-layer-large-scale-pagination-plan.md` ← 大数据量分页优化

### 追溯变更与规则
18. `../governance/operations-log.md` ← 关键操作与文档更新留痕
19. `../governance/document-update-rules.md` ← 文档更新约束规则

---

## 六、文档分类说明

### 事实类文档 (`reference/`)
记录代码当前状态、实体结构、接口清单、配置入口，**不包含判断与建议**。
- `semantic-layer-baseline.md` ← 语义层事实基线
- `semantic-glossary.md` ← 术语与边界定义
- `dataagent-architecture-baseline.md` ← DataAgent 架构事实

### 判断类文档 (`reports/`)
记录调研结论、缺口分析、外部对标、专题方案，**包含判断但不直接指导执行**。
- `dataagent-overall-gap-report.md` ← 整体缺口判断
- `dataagent-industry-benchmarks.md` ← 行业对标结论
- `dataagent-agent-backend-report.md` ← Agent 主链路判断
- `semantic-layer-full-report.md` ← 语义层完整分析

### 规划类文档 (`planning/`)
记录路线图、任务清单、进度追踪，**直接指导执行顺序**。
- `dataagent-master-roadmap.md` ← 总体建设路线图
- `semantic-layer-roadmap.md` ← 语义层实施路线图
- `phase1-task-list.md` ← Phase 1 任务清单
- `phase1-progress.md` ← Phase 1 进度追踪

### 摘要类文档 (`summary/`)
记录快速入口、当前审查、文档导航，**面向快速理解**。
- `dataagent-doc-map.md` ← 本文档
- `context-summary-semantic-service.md` ← 当前现状摘要
- `verification-report.md` ← 当前代码审查快照

### 治理类文档 (`governance/`)
记录文档规则、操作留痕，**确保文档体系可持续维护**。
- `document-update-rules.md` ← 文档更新约束规则
- `operations-log.md` ← 关键操作与文档更新留痕

---

## 七、常见问题快速定位

### Q1: DataAgent 当前核心链路是什么？
**A**: `get_tables -> get_table_schema -> execute_sql`，详见 `../reference/dataagent-architecture-baseline.md` 第二节。

### Q2: 配置入口和依赖入口在哪里？
**A**: 
- 配置：`data-agent-backend/src/main/resources/application.properties`
- 依赖：`data-agent-backend/pom.xml`
- 详见 `../reference/semantic-layer-baseline.md` 第九节。

### Q3: 为什么要参考 dbt、Cube、Superset？
**A**: 
- dbt/MetricFlow：指标语义建模标杆
- Cube：语义层产品化标杆
- Superset：查询产品化标杆
- 详见 `../reports/dataagent-industry-benchmarks.md` 第二节。

### Q4: 哪些文档记录事实，哪些文档记录路线图？
**A**: 
- 事实 → `reference/`
- 判断 → `reports/`
- 路线图 → `planning/`
- 详见本文档第六节。

### Q5: 当前最优先要做的三件事是什么？
**A**: 
1. 补测试基线
2. 补大数据量分页与缓存优化
3. 补显式 datasource 路由
- 详见 `../reports/dataagent-executive-summary.md` 第六节。

---

## 八、配套资源

### 代码关键路径
- Agent 主链路：`data-agent-backend/src/main/java/io/github/malonetalk/agent/AgentService.java`
- 语义层服务：`data-agent-backend/src/main/java/io/github/malonetalk/service/SemanticSchemaService.java`
- SQL 执行：`data-agent-backend/src/main/java/io/github/malonetalk/agent/datasource/SqlExecutor.java`
- 配置入口：`data-agent-backend/src/main/resources/application.properties`

### 外部参考
- dbt Semantic Layer：https://docs.getdbt.com/docs/use-dbt-semantic-layer/dbt-sl
- Cube：https://cube.dev/docs/
- Apache Superset：https://superset.apache.org/
- DB-GPT：https://github.com/eosphoros-ai/DB-GPT
- OpenMetadata：https://github.com/open-metadata/OpenMetadata

---

## 九、文档维护规则

当代码发生变化时，请参考 `../governance/document-update-rules.md` 同步更新相关文档。

核心原则：
- 事实变化 → 更新 `reference/`
- 判断变化 → 更新 `reports/`
- 路线变化 → 更新 `planning/`
- 重要变更 → 记录 `governance/operations-log.md`

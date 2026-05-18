# DataAgent `.claude` 文档建设路线图

生成时间：2026-05-12  
用途：记录 `.claude` 文档体系的建设顺序、目标、输入来源与避免重复的边界

---

## 一、文档建设原则

### 1. 职责单一
- 事实只写 `reference/`
- 判断与差距只写 `reports/`
- 路线图只写 `planning/`
- 摘要与入口只写 `summary/`
- 规则与留痕只写 `governance/`

### 2. 避免重复
- 同一段代码链路描述不能出现在多份文档
- 优先引用，而非复制粘贴
- 摘要文档只做索引，不展开详版分析

### 3. 可追溯
- 每条核心结论都必须能追溯到现有代码或既有报告
- 避免无证据判断

### 4. 可维护
- 文档更新规则明确（参见 `../governance/document-update-rules.md`）
- 操作留痕（参见 `../governance/operations-log.md`）

---

## 二、已完成文档清单

### summary/ (摘要与入口)
- ✅ `dataagent-doc-map.md` (2026-05-12)：DataAgent 文档导航入口
- ✅ `context-summary-semantic-service.md` (2026-05-10)：当前现状摘要
- ✅ `verification-report.md` (2026-05-10)：当前代码审查快照

### reference/ (事实基线与术语)
- ✅ `dataagent-architecture-baseline.md` (2026-05-12)：DataAgent 架构事实基线
- ✅ `analysis-result-schema.md` (2026-05-12)：分析结果对象模型规范
- ✅ `semantic-layer-baseline.md` (2026-05-10, 2026-05-12 增补)：语义层事实基线
- ✅ `semantic-glossary.md` (2026-05-10)：术语与边界定义

### reports/ (完整报告与专题方案)
- ✅ `result-layer-benchmark.md` (2026-05-12)：结果层产品化参考
- ✅ `dataagent-industry-benchmarks.md` (2026-05-10, 2026-05-12 增补)：行业对标参考
- ✅ `dataagent-overall-gap-report.md` (2026-05-10)：整体缺口调研
- ✅ `dataagent-agent-backend-report.md` (2026-05-10)：Agent 主链路调研
- ✅ `dataagent-datasource-execution-report.md` (2026-05-10)：数据源与执行链调研
- ✅ `dataagent-platform-readiness-report.md` (2026-05-10)：前端、测试与平台完备度
- ✅ `dataagent-master-roadmap.md` (2026-05-10)：总体建设路线图
- ✅ `dataagent-executive-summary.md` (2026-05-10)：建设执行摘要
- ✅ `dataagent-multi-datasource-routing-report.md` (2026-05-10)：多数据源路由调研
- ✅ `dataagent-async-query-report.md` (2026-05-10)：异步查询与结果层调研
- ✅ `dataagent-observability-governance-report.md` (2026-05-10)：可观测性与治理调研
- ✅ `dataagent-execution-safety-report.md` (2026-05-10)：SQL 安全与执行治理调研
- ✅ `dataagent-testing-strategy-report.md` (2026-05-10)：测试策略调研
- ✅ `dataagent-deployment-readiness-report.md` (2026-05-10)：部署与生产就绪度调研
- ✅ `dataagent-metric-semantics-report.md` (2026-05-10)：指标语义建模调研
- ✅ `semantic-layer-full-report.md` (2026-05-10)：语义层完整分析报告
- ✅ `semantic-layer-large-scale-pagination-plan.md` (2026-05-10)：大数据量分页优化方案

### planning/ (路线图、任务清单、进度追踪)
- ✅ `dataagent-claude-doc-roadmap.md` (2026-05-12)：本文档
- ✅ `semantic-layer-roadmap.md` (2026-05-10)：语义层实施路线图
- ✅ `phase1-task-list.md` (2026-05-10)：Phase 1 任务清单
- ✅ `phase1-progress.md` (2026-05-10)：Phase 1 进度追踪

### governance/ (规则与留痕)
- ✅ `document-update-rules.md` (2026-05-10, 2026-05-12 增补)：文档更新约束规则
- ✅ `operations-log.md` (2026-05-08 至 2026-05-12)：关键操作与文档更新留痕

---

## 三、待补强文档清单（基于外部对标映射）

### P0 - 核心能力文档（优先级最高）

#### 1. `reference/agent-execution-contract.md`
**目标**：定义 Agent 执行协议  
**输入来源**：
- `dataagent-architecture-baseline.md` 第三节
- `dataagent-agent-backend-report.md`
- DB-GPT 工作流参考

**主要章节**：
- 用户问题进入后分几步
- 每步允许调用哪些工具
- 何时必须先看 schema
- 何时可以直接生成 SQL
- 何时需要追问
- 最终输出结构是什么

**避免重复**：不重复粘贴 `AgentService.java` 代码，只引用关键方法

---

#### 2. `reference/analysis-result-schema.md`
**目标**：定义分析结果对象模型  
**输入来源**：
- Vanna 结果对象参考
- Superset 结果层参考
- `dataagent-async-query-report.md`

**主要章节**：
- 文本总结
- SQL
- 表格结果
- 图表推荐
- follow-up questions
- 执行元数据
- datasourceId / sessionId
- 可复核信息

**避免重复**：不与 `dataagent-async-query-report.md` 重复展开判断，只定义结构

---

#### 3. `governance/sql-safety-and-execution-contract.md`
**目标**：定义 SQL 执行与治理规范  
**输入来源**：
- `dataagent-execution-safety-report.md`
- SQLMesh 参考
- `SqlExecutor.java`

**主要章节**：
- 只读边界
- SQL 风险分类
- explain / limit / timeout
- 异步任务化策略
- 审计日志字段
- 查询取消与结果续取

**避免重复**：不重复粘贴 `SqlExecutor.java` 代码，只引用关键逻辑

---

### P1 - 指标与语义层演进文档

#### 4. `reference/metric-semantic-model.md`
**目标**：定义指标语义模型规范  
**输入来源**：
- dbt Semantic Layer / MetricFlow 参考
- `dataagent-metric-semantics-report.md`
- `semantic-glossary.md`

**主要章节**：
- metric / measure / dimension / relation 的边界
- 最小字段集
- 时间维度
- 默认过滤器
- metric 依赖
- Agent 如何选择 metric-first 还是 SQL-first

**避免重复**：不与 `dataagent-metric-semantics-report.md` 重复展开判断

---

#### 5. `planning/metric-semantic-evolution.md`
**目标**：记录从表/列/关系语义演进到指标语义的分阶段路线  
**输入来源**：
- `dataagent-master-roadmap.md`
- `dataagent-metric-semantics-report.md`
- dbt Semantic Layer 参考

**主要章节**：
- Phase 1: 表/列/关系语义（已完成）
- Phase 2: 高级字段语义
- Phase 3: 指标语义层
- Phase 4: 治理层

**避免重复**：不与 `dataagent-master-roadmap.md` 重复，只聚焦指标语义演进

---

### P2 - 产品化与 UI 契约文档

#### 6. `reports/result-layer-benchmark.md`
**目标**：查询结果层产品化参考  
**输入来源**：
- Superset 结果层参考
- `dataagent-async-query-report.md`

**主要章节**：
- 查询历史
- 结果缓存
- 下载导出
- 图表切换
- 异步执行
- 用户复核 SQL

**避免重复**：不与 `dataagent-async-query-report.md` 重复展开判断

---

#### 7. `reports/semantic-layer-to-ui-contract.md`
**目标**：语义层对外暴露的结构化 API 契约  
**输入来源**：
- Cube API 参考
- Lightdash 参考
- `semantic-layer-baseline.md`

**主要章节**：
- 字段展示名
- 指标说明
- 可用过滤器
- 时间粒度
- 排序/分页
- 图表推荐元信息

**避免重复**：不重复粘贴 `SemanticSchemaService.java` 代码

---

### P3 - 治理与元数据文档

#### 8. `planning/governance-layer-roadmap.md`
**目标**：治理层演进路线  
**输入来源**：
- OpenMetadata / DataHub 参考
- `dataagent-observability-governance-report.md`

**主要章节**：
- glossary
- lineage
- metric
- ownership
- audit
- version
- draft-published

**避免重复**：不与 `dataagent-master-roadmap.md` 重复，只聚焦治理层

---

## 四、文档更新顺序建议

### 第一批（立即补强）
1. `reference/agent-execution-contract.md`
2. `reference/analysis-result-schema.md`
3. `governance/sql-safety-and-execution-contract.md`

**理由**：这三份文档直接影响当前 Agent 主链路、结果层和 SQL 执行的规范化，是最紧迫的。

### 第二批（短期补强）
4. `reference/metric-semantic-model.md`
5. `planning/metric-semantic-evolution.md`

**理由**：指标语义是语义层演进的下一步，需要尽早明确模型。

### 第三批（中期补强）
6. `reports/result-layer-benchmark.md`
7. `reports/semantic-layer-to-ui-contract.md`

**理由**：产品化与 UI 契约是前端落地的前置条件。

### 第四批（长期补强）
8. `planning/governance-layer-roadmap.md`

**理由**：治理层是长期演进方向，可以稍后补强。

---

## 五、去重规则

### 1. 代码链路描述
- 只在 `reference/` 中记录代码事实
- `reports/` 中只引用，不重复粘贴

### 2. 判断与建议
- 只在 `reports/` 中记录判断
- `reference/` 中不包含判断

### 3. 路线图
- 只在 `planning/` 中记录路线图
- `reports/` 中不重复展开路线图

### 4. 外部参考
- 只在 `reports/dataagent-industry-benchmarks.md` 中记录外部对标
- 其他文档只引用，不重复展开

---

## 六、验收清单

### 文档质量验收
- [ ] 每条核心结论都能追溯到现有代码或既有报告
- [ ] 新增文档与现有文档职责单一，无大段重复
- [ ] 新成员按 `summary -> reference -> reports -> planning` 路径阅读，能在 1-2 跳内定位核心问题
- [ ] 文档中明确区分"事实 / 判断 / 规划"边界

### 核心问题可快速回答
- [ ] DataAgent 当前核心链路是什么？
- [ ] 配置入口和依赖入口在哪里？
- [ ] 为什么要参考 dbt、Cube、Superset？
- [ ] 哪些文档记录事实，哪些文档记录路线图？
- [ ] 当前最优先要做的三件事是什么？

---

## 七、后续维护规则

### 代码变更时
参见 `../governance/document-update-rules.md`

### 文档新增时
1. 确认文档类型（事实/判断/规划/摘要/治理）
2. 确认放置目录（`reference/` / `reports/` / `planning/` / `summary/` / `governance/`）
3. 更新本文档的"已完成文档清单"
4. 更新 `../README.md` 的文档索引
5. 记录到 `../governance/operations-log.md`

### 文档更新时
1. 确认是否需要同步更新其他文档（参见 `../governance/document-update-rules.md`）
2. 记录到 `../governance/operations-log.md`

---

## 八、总结

当前 `.claude` 文档体系已经较为完善，核心缺口是：
1. Agent 执行协议文档
2. 分析结果对象模型文档
3. SQL 执行治理规范文档

建议优先补强这三份文档，然后再逐步补强指标语义、产品化与治理层文档。

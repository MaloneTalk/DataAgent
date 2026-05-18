# 文档更新约束规则

生成时间：2026-05-10  
最后更新：2026-05-12  
用途：规定当代码发生变化时，`.claude` 分类文档应如何同步更新。

---

# 一、目标

当前 `.claude` 文档体系已经拆分为：
- `summary/`：摘要与当前审查
- `reference/`：事实基线与术语定义
- `reports/`：完整报告与专题方案
- `planning/`：路线图、任务清单、进度追踪
- `governance/`：规则与留痕

为了避免后续代码继续演进时文档再次漂移，本文件定义：

> **改哪类代码，必须同步检查和更新哪些文档。**

---

# 二、DataAgent 专项规则（2026-05-12 新增）

## 核心原则
- **事实只写 `reference/`**：代码当前状态、实体结构、接口清单、配置入口
- **判断与差距只写 `reports/`**：调研结论、缺口分析、外部对标、专题方案
- **路线图只写 `planning/`**：路线图、任务清单、进度追踪
- **`summary/` 只做入口与摘要**：快速入口、当前审查、文档导航
- **禁止在多份文档重复粘贴同一段代码链路描述**：优先引用，而非复制

## 文档职责边界
| 文档类型 | 职责 | 不应包含 |
|---------|------|---------|
| `reference/` | 代码事实、实体结构、接口清单 | 判断、建议、路线图 |
| `reports/` | 调研结论、缺口分析、外部对标 | 代码事实、路线图 |
| `planning/` | 路线图、任务清单、进度追踪 | 代码事实、判断 |
| `summary/` | 快速入口、当前审查、文档导航 | 详版分析、代码事实 |
| `governance/` | 规则、留痕 | 代码事实、判断、路线图 |

---

# 三、顶层文档职责速记

| 文档 | 作用 | 更新触发条件 |
|---|---|---|
| `../README.md` | 文档索引与目录说明 | 文档体系结构变化时 |
| `../summary/context-summary-semantic-service.md` | 快速摘要 | DataAgent 或语义层核心现状变化时 |
| `../reference/semantic-layer-baseline.md` | 当前事实基线 | 代码事实变化时 |
| `../reference/semantic-glossary.md` | 术语与边界定义 | 新概念或语义边界变化时 |
| `../reports/semantic-layer-full-report.md` | 语义层完整分析报告 | 语义层阶段性调研结论变化时 |
| `../reports/semantic-layer-large-scale-pagination-plan.md` | 分页优化专题方案 | 分页/缓存方案变化时 |
| `../reports/dataagent-overall-gap-report.md` | DataAgent 整体缺口报告 | 整体能力判断变化时 |
| `../reports/dataagent-master-roadmap.md` | DataAgent 总体建设路线图 | 整体优先级或阶段划分变化时 |
| `../reports/dataagent-industry-benchmarks.md` | 行业对标参考 | 外部对标结论变化时 |
| `../planning/semantic-layer-roadmap.md` | 实施路线图 | 阶段目标变化时 |
| `../planning/phase1-task-list.md` | Phase 1 任务清单 | Phase 1 范围变化时 |
| `../planning/phase1-progress.md` | Phase 1 进度追踪 | Phase 1 进度变化时 |
| `../summary/verification-report.md` | 当前代码审查快照 | 当前代码状态评价变化时 |
| `operations-log.md` | 留痕 | 每次重要文档重构或收口时 |

---

# 三、代码变更 -> 文档更新映射

## 1. 修改语义层实体 / 表结构时
涉及文件：
- `entity/*.java`
- `sql/data_source.sql`
- `resources/mapper/*.xml`

### 必须检查
- `../reference/semantic-layer-baseline.md`
- `../summary/context-summary-semantic-service.md`
- `../summary/verification-report.md`

### 视情况更新
- `../reference/semantic-glossary.md`
- `../reports/semantic-layer-full-report.md`
- `../reports/dataagent-overall-gap-report.md`

---

## 2. 修改 DTO / tool 输出时
涉及文件：
- `dto/semantic/*.java`
- `dto/toolresponse/*.java`
- `dto/tool/*.java`

### 必须检查
- `../reference/semantic-layer-baseline.md`
- `../reference/semantic-glossary.md`
- `../summary/context-summary-semantic-service.md`
- `../summary/verification-report.md`

### 视情况更新
- `../reports/semantic-layer-full-report.md`
- `../reports/dataagent-agent-backend-report.md`

---

## 3. 修改 Controller 接口时
涉及文件：
- `controller/*.java`

### 必须检查
- `../reference/semantic-layer-baseline.md`
- `../summary/context-summary-semantic-service.md`
- `../summary/verification-report.md`

### 视情况更新
- `../planning/semantic-layer-roadmap.md`
- `../planning/phase1-task-list.md`
- `../planning/phase1-progress.md`
- `../reports/dataagent-platform-readiness-report.md`

---

## 4. 修改 `SemanticVisibilitySupport` / `SemanticSchemaService` 时
涉及文件：
- `service/SemanticVisibilitySupport.java`
- `service/SemanticSchemaService.java`

### 必须检查
- `../summary/context-summary-semantic-service.md`
- `../reference/semantic-layer-baseline.md`
- `../summary/verification-report.md`

### 强烈建议同步更新
- `../reports/semantic-layer-full-report.md`
- `../reports/semantic-layer-large-scale-pagination-plan.md`
- `../reports/dataagent-overall-gap-report.md`
- `../planning/semantic-layer-roadmap.md`
- `operations-log.md`

---

## 5. 修改 Agent / tool / 会话输出时
涉及文件：
- `agent/AgentService.java`
- `agent/tools/*.java`
- `utils/MsgUtils.java`
- `controller/AgentController.java`

### 必须检查
- `../reference/semantic-layer-baseline.md`
- `../summary/context-summary-semantic-service.md`
- `../summary/verification-report.md`

### 视情况更新
- `../reports/dataagent-agent-backend-report.md`
- `../reports/dataagent-overall-gap-report.md`
- `../reports/dataagent-master-roadmap.md`
- `../planning/semantic-layer-roadmap.md`

---

## 6. 修改数据源 / 执行安全 / SQL 能力时
涉及文件：
- `Datasource*`
- `DynamicDataSourceManager.java`
- `SchemaReader.java`
- `SqlExecutor.java`
- `DataSourceConfig.java`
- `application.properties`

### 必须检查
- `../reference/semantic-layer-baseline.md`
- `../summary/context-summary-semantic-service.md`
- `../summary/verification-report.md`

### 强烈建议同步更新
- `../reports/dataagent-datasource-execution-report.md`
- `../reports/dataagent-overall-gap-report.md`
- `../reports/dataagent-master-roadmap.md`
- 如涉及性能：`../reports/semantic-layer-large-scale-pagination-plan.md`

---

## 7. 修改前端 / 测试 / CI / 配置治理时
涉及文件：
- `data-agent-frontend/**/*`
- `src/test/**/*`
- `.github/workflows/*.yml`
- `pom.xml`
- `application.properties`

### 必须检查
- `../reference/semantic-layer-baseline.md`
- `../summary/context-summary-semantic-service.md`
- `../summary/verification-report.md`

### 强烈建议同步更新
- `../reports/dataagent-platform-readiness-report.md`
- `../reports/dataagent-overall-gap-report.md`
- `../reports/dataagent-master-roadmap.md`
- `../planning/phase1-task-list.md`
- `../planning/phase1-progress.md`

---

## 8. 引入新高级语义概念时
例如：
- display name
- semantic type
- metric
- lineage
- draft / published
- audit log

### 必须检查
- `../reference/semantic-glossary.md`
- `../reference/semantic-layer-baseline.md`
- `../summary/context-summary-semantic-service.md`

### 强烈建议更新
- `../reports/semantic-layer-full-report.md`
- `../reports/dataagent-industry-benchmarks.md`
- `../planning/semantic-layer-roadmap.md`
- `../planning/phase1-task-list.md`

---

# 四、最小更新策略

## 1. 小改动
只更新：
- `../reference/semantic-layer-baseline.md`
- `../summary/verification-report.md`

## 2. 中等改动
更新：
- `../summary/context-summary-semantic-service.md`
- `../reference/semantic-layer-baseline.md`
- `../summary/verification-report.md`
- `operations-log.md`

## 3. 架构级改动
更新：
- `../reference/semantic-glossary.md`
- `../summary/context-summary-semantic-service.md`
- `../reference/semantic-layer-baseline.md`
- `../reports/semantic-layer-full-report.md`
- `../reports/dataagent-overall-gap-report.md`
- `../reports/dataagent-master-roadmap.md`
- `../summary/verification-report.md`
- `operations-log.md`

---

# 五、推荐执行规则

后续每次做完 DataAgent / 语义层相关代码修改，最少回答这 5 个问题：

1. **当前事实变了吗？**
   - 如果变了，更新 `../reference/semantic-layer-baseline.md`
2. **当前快速摘要还准确吗？**
   - 如果不准确，更新 `../summary/context-summary-semantic-service.md`
3. **当前评估结论还成立吗？**
   - 如果不成立，更新 `../summary/verification-report.md`
4. **是否引入了新概念或新边界？**
   - 如果有，更新 `../reference/semantic-glossary.md`
5. **是否改变了整体 DataAgent 路线判断？**
   - 如果有，更新 `../reports/dataagent-overall-gap-report.md` 与 `../reports/dataagent-master-roadmap.md`

---

# 六、结论

本文件的目标不是增加负担，而是把文档同步动作标准化。

> 以后 DataAgent 代码继续演进时，只要按本文件映射关系做最小同步，就能避免 `.claude` 再次出现“代码变了、文档还停留在旧阶段”的问题。
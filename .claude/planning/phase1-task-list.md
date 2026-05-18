# Phase 1 执行任务清单

生成时间：2026-05-10  
适用范围：当前语义层骨架的稳定化与维护性补全  
来源：基于 `semantic-layer-roadmap.md` 的 Phase 1 拆解

---

# 一、Phase 1 目标

把当前已经具备的：
- 表语义
- 列语义
- 关系语义
- 物理外键读取
- Agent 结构化输出

从“功能已形成”提升到：

> **稳定可维护、可验证、可持续扩展的基线。**

---

# 二、任务拆分

## A. 测试基线建设

### A1. 为 `SemanticVisibilitySupport` 建测试
#### 目标
验证表 / 列 merge 与可见性规则不会回归。

#### 建议覆盖
- mergeTables：物理表 + 表语义 merge
- mergeColumns：物理列 + 列语义 merge
- 同名语义表按更新时间择优
- 同名语义列覆盖物理列
- 表可见性判断
- 列可见性判断

#### 交付
- `src/test/.../SemanticVisibilitySupportTest.java`

---

### A2. 为 `SemanticSchemaService` 建测试
#### 目标
验证语义聚合主链路不会回归。

#### 建议覆盖
- 表语义列表输出
- 列语义列表输出
- `getVisibleTablePrompts`
- `getTableSchema`
- 物理关系 + 逻辑关系 merge
- 逻辑关系覆盖物理关系
- 不可见表 / 列 / 关系被正确过滤
- resetTableSemantic / resetColumnSemantic / 批量 reset

#### 交付
- `src/test/.../SemanticSchemaServiceTest.java`

---

### A3. 为 `LogicalTableRelationServiceImpl` 建测试
#### 目标
验证关系管理层与候选项校验逻辑。

#### 建议覆盖
- listCandidateTables
- listCandidateColumns
- create
- update
- updateEnabled
- delete
- deleteBatch
- `effective=true`
- `effective=false` + `invalidReason`
- source/target 列数量不一致校验
- source 列重复校验

#### 交付
- `src/test/.../LogicalTableRelationServiceImplTest.java`

---

## B. 迁移与环境升级能力

### B1. 补旧库升级脚本
#### 目标
让已有环境可以从旧版本平滑升级到当前表 / 列 / 关系语义结构。

#### 任务
- 为 `table_info` 增量补字段 migration 方案
- 为 `column_info` 增量补表/索引 migration 方案
- 为 `logical_table_relation` 增量补建表方案

#### 交付
- 独立 migration SQL（推荐）
- 或明确版本化升级说明

---

### B2. 统一初始化与升级说明
#### 目标
明确：
- 新环境怎么初始化
- 老环境怎么升级

#### 交付
- 在 `../governance/` 或项目 SQL 目录补一份迁移说明

---

## C. 接口运营效率补全

### C1. 表语义列表分页 / 搜索 / 排序
#### 目标
避免表数量上来后难维护。

#### 任务
- 支持 `keyword`
- 支持分页参数
- 支持排序字段 / 排序方向

#### 交付
- 表语义管理接口升级
- 对应 DTO / service / mapper 改造

---

### C2. 列语义列表分页 / 搜索 / 排序
#### 目标
提高列语义维护效率。

#### 任务
- 支持列名 / 描述搜索
- 支持分页
- 支持排序

---

### C3. 关系语义列表分页 / 搜索 / 排序
#### 目标
提高关系语义维护效率。

#### 任务
- 支持 source / target 搜索
- 支持 enabled / effective 筛选
- 支持分页与排序

---

### C4. 批量操作
#### 目标
减少单条维护成本。

#### 优先补
- 表语义批量重置（若未完全具备则收口）
- 列语义批量重置
- 关系批量启用/禁用
- 表 / 列批量 visible 更新

---

## D. 代码结构收尾

### D1. 统一实现层包结构
#### 目标
让所有实现类都在 `service.impl`

#### 当前关注
- `ColumnSemanticInfoServiceImpl`

#### 交付
- 包结构统一
- 引用更新

---

### D2. 文档同步
#### 目标
代码收口后，保持 `.claude` 文档与代码一致。

#### 必须同步检查
- `../reference/semantic-layer-baseline.md`
- `../summary/context-summary-semantic-service.md`
- `../summary/verification-report.md`
- `../governance/operations-log.md`

如涉及规则变化，再检查：
- `../governance/document-update-rules.md`

---

## E. Agent 输出可观测性预研

### E1. 评估 SSE 结构化事件流改造
#### 目标
为后续前端结构化渲染做准备。

#### 当前问题
- `chat/stream` 返回 `Flux<String>`
- reasoning / tool result / final answer 无法区分

#### Phase 1 要求
本阶段不一定落地实现，但至少要：
- 明确现状限制
- 明确后续改造接口形态
- 决定是否拆成独立任务进入 Phase 2 之前执行

---

# 三、建议执行顺序

## 第一步：测试
1. `SemanticVisibilitySupportTest`
2. `SemanticSchemaServiceTest`
3. `LogicalTableRelationServiceImplTest`

## 第二步：迁移
4. 补旧库升级 SQL
5. 补升级说明

## 第三步：接口运营效率
6. 表语义分页/搜索/排序
7. 列语义分页/搜索/排序
8. 关系语义分页/搜索/排序
9. 批量操作能力

## 第四步：结构收尾
10. 统一 `service.impl`
11. 同步 `.claude` 文档

## 第五步：Agent 可观测性预研
12. 确认 SSE 结构化事件流改造方案

---

# 四、完成判定标准

Phase 1 完成时，应至少满足：
1. 有 `src/test` 目录且关键语义链路有自动化测试
2. 已有环境具备明确升级路径
3. 表 / 列 / 关系管理接口具备基本运营效率能力
4. 实现层包结构基本统一
5. `.claude` 文档已与代码同步
6. SSE 结构化改造方向已有清晰结论

---

# 五、当前推荐立刻开工项

如果现在就开始，我建议先做：

### 任务 1
建立 `src/test` 目录并补第一批语义层测试

### 任务 2
补旧库升级 SQL / migration 方案

### 任务 3
补表 / 列 / 关系管理接口的分页与搜索

这是当前收益最高、风险最低的起步顺序。
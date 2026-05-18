# DataAgent / 语义层实施路线图

生成时间：2026-05-10  
**最后更新：2026-05-17**  
适用范围：基于当前最新代码的整体 DataAgent 与语义层后续建设  
配套详版分析：见 `../reports/semantic-layer-full-report.md` 与 `../reports/dataagent-master-roadmap.md`  
**新增实现方案调研**：见 `../reports/semantic-layer-implementation-research.md`

---

# 一、目标

当前代码已经具备：
- 表语义
- 列语义
- 关系语义
- 物理外键读取
- 语义 merge
- Agent 结构化输出
- 基础数据源管理与 SQL 执行

后续路线不再是“从 0 到 1 建语义层”，而是：

> **从语义化查数助手后端骨架，升级到完整的 DataAgent 产品与平台。**

---

# 二、路线图总览

## Phase 0：收口当前基线
目标：把当前骨架变成稳定可持续迭代的基线。

### 任务
1. 补测试
2. 补 migration / 旧库升级方案
3. 大数据量分页与缓存优化
4. 给表 / 列 / 关系接口补分页、搜索、排序
5. 补批量启用/禁用与批量操作
6. 统一实现层包结构
7. 文档同步规则固化

### 交付结果
- 当前系统从“能跑”变成“可维护”

---

## Phase 1：数据源与执行安全升级
目标：让系统更稳地跑在多数据源与更真实环境里。

### 任务
1. 显式 datasource 路由
2. 数据源连通性测试
3. 数据源健康检查
4. 密码脱敏 / secrets 管理
5. 查询审计日志
6. 更强 SQL 校验
7. 查询成本保护

### 交付结果
- 从开发演示态提升到可控执行态

---

## Phase 2：前端与交互闭环
目标：让 DataAgent 从后端能力变成真正可使用的产品。

### 任务
1. 前端真实工程落地
2. 聊天页 + SSE 渲染
3. 数据源管理页
4. 表 / 列 / 关系语义管理页
5. 候选表 / 字段选择器
6. 查询结果与错误展示

### 交付结果
- 形成最小产品闭环

---

## Phase 3：Agent 产品化增强
目标：让 Agent 成为任务型数据代理，而不是只调用三个工具的查数助手。

### 任务
1. 结构化事件流
2. 查询计划展示
3. SQL 解释
4. 异步长查询任务
5. 查询取消
6. 结果续取 / 下载
7. 查询历史 / 收藏

### 交付结果
- Agent 从查数助手升级为数据代理产品核心

---

## Phase 4：语义层升级为分析语义层
目标：让 DataAgent 不只是理解结构，而是开始理解业务分析语义。

### 任务（详细实现方案见 `../reports/semantic-layer-implementation-research.md`）

#### 4.1 高级字段语义（column_info ALTER TABLE，改动最小）
- `semantic_type`（DIMENSION/MEASURE/TIME/IDENTIFIER/ENUM/FOREIGN_KEY/STATUS）
- `time_role`（EVENT_TIME/PROCESS_TIME/REPORT_DATE/FISCAL_PERIOD/EXPIRY_TIME/PARTITION_KEY）
- `display_name`（中文业务名）
- `enum_schema`（枚举定义 JSON）
- `example_values`（示例值 JSON 数组，按 semantic_type 差异化渲染）

#### 4.2 表角色与过滤增强（table_info ALTER TABLE）
- `table_role`（FACT/DIMENSION/BRIDGE/AGGREGATE/LOG/CONFIG）
- `preferred_time_column`（首选时间过滤列）
- `partition_column`（分区键，查询必须包含）
- `estimated_row_count`（数量级提示：<1K/<1M/<100M/>100M）
- `recommended_filters`（JSON 数组：SOFT_DELETE/PARTITION/MANDATORY 三类）

#### 4.3 关系推理增强（logical_table_relation ALTER TABLE）
- `is_preferred` + `priority`（首选/强制 JOIN 路径标记）
- `join_hint`（给 LLM 的自然语言使用场景说明）
- `usage_context`（适用场景描述）

#### 4.4 指标语义层（新增 metric_definition 表 + GetMetricsTool）
- `MetricDefinition` 实体：metricName/displayName/description/metricType/sourceTable/expression/aggregation/filterCondition/dimensionColumns/timeColumn
- aggregation 类型：SUM/COUNT/COUNT_DISTINCT/AVG/MAX/MIN/RATIO/CUSTOM
- `MetricSqlBuilder`：按 aggregation 类型生成完整 SQL 聚合片段
- `GetMetricsTool`：新 Agent 工具，`get_metrics`，返回预渲染的 `MetricPromptItem` 列表

### 交付结果
- Agent 从结构 SQL 生成升级到分析 SQL 推理
- 对维度/指标区分、时间列选择、分区过滤的 SQL 准确率显著提升

---

## Phase 5：治理与平台完备化
目标：让系统具备长期演进、多人协作与生产运维能力。

### 任务（详细实现方案见 `../reports/semantic-layer-implementation-research.md` 第四、五节）

#### 5.1 语义层治理能力
- 三张主表加 `status`（draft/published/deprecated/archived）+ `published_at` + `published_by`
- 三张主表加软删除字段：`is_deleted` + `deleted_time` + `deleted_by`
- 新建 `semantic_audit_log` 表（append-only）+ Spring AOP 切面自动记录变更
- `SemanticCatalog` 默认只加载 `status=published, is_deleted=0` 的语义

#### 5.2 Completeness 统计 API
- `SemanticCompletenessService`：实时计算，6 个维度加权（表描述/列描述/table_role/preferred_time_col/关系/发布比例）
- 单独输出 `ai_readiness_score`（只计算 AI 推理相关字段的覆盖率）
- `GET /api/tableinfo/semantic/completeness?datasourceId=` 接口

#### 5.3 平台能力
- metrics / tracing / Spring Actuator
- 发布链路与回滚策略
- 环境分层配置

### 交付结果
- 语义层从”配置层”升级为”可审计的治理层”
- 支持多人协作、可追溯、可回滚

---

# 三、优先级建议

## P0：现在最该做
1. 测试基线
2. 大数据量分页 / schema 缓存
3. migration / 升级方案
4. 显式 datasource 路由

## P1：紧随其后
5. 数据源安全与审计
6. 前端真实工程
7. 结构化 SSE 事件流

## P2：产品化增强
8. 异步长查询任务
9. 查询历史 / 收藏 / 导出
10. Agent 计划与解释能力

## P3：高级语义
11. 高级字段语义
12. 指标语义层
13. 分析增强语义

## P4：治理层
14. 审计 / 版本 / draft / published
15. 平台级可观测性与发布治理

---

# 四、实施原则

1. **先稳住现有骨架，再扩业务语义**
2. **不要把指标语义硬塞进列语义表**
3. **Agent 输出增强必须与管理模型解耦**
4. **缓存与分页优化属于产品能力，不只是性能补丁**
5. **治理能力应在结构与业务语义稳定后推进**

---

# 五、当前推荐下一步

如果现在立刻开工，建议优先进入：
1. 测试基线
2. 迁移方案
3. 大数据量分页 / schema 缓存
4. 分页 / 搜索 / 批量操作
5. 显式 datasource 路由

这样风险最低，也最符合当前代码的自然演进方向。
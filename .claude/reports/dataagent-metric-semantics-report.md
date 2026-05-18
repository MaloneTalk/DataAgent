# DataAgent 指标语义建模调研报告

生成时间：2026-05-10  
范围：指标语义层如何在当前表 / 列 / 关系语义骨架上扩展

---

# 一、当前现状

当前语义层已具备：
- 表语义
- 列语义
- 关系语义

但当前仍缺：
- 指标定义
- 指标口径
- 聚合方式
- 过滤规则
- 指标依赖

这意味着当前 DataAgent 仍更擅长：
- 结构型 SQL 辅助

而不是：
- 业务指标问答
- 口径型分析推理

---

# 二、为什么指标语义必须独立建模

行业上最重要的参考是：
- dbt Semantic Layer / MetricFlow

其核心启发是：
- metric 不应等于列
- metric 应该是独立语义对象
- 需要明确：
  - measures
  - dimensions
  - metrics
  - dependencies

也就是说：

> 指标语义不能继续塞进 `ColumnSemanticInfo`，否则字段语义和业务口径会混乱。

---

# 三、当前代码里最适合承接指标语义的位置

## 1. 持久化层
建议新增独立实体：
- `MetricDefinition`

不要扩在：
- `TableInfo`
- `ColumnSemanticInfo`
- `LogicalTableRelation`

## 2. 服务层
建议新增：
- `MetricService`
- `MetricSupport`

## 3. 聚合层
指标最终仍应通过：
- `SemanticSchemaService`

或后续独立的 schema assembler 对外暴露给 Agent。

## 4. Agent / tool 层
建议后续新增：
- `get_metrics`
- `get_metric_definition`

而不是直接做 `query_metric`。

---

# 四、推荐的最小边界

## 最小 MetricDefinition 字段建议
- `metricName`
- `displayName`
- `description`
- `datasourceId`
- `baseTableName`
- `expression`
- `aggregationType`
- `timeDimension`
- `defaultFilterJson`
- `isActive`

## 管理 DTO 最小集合
- `MetricUpsertRequest`
- `MetricResponse`

## Agent 输出 DTO 最小集合
- `MetricDefinitionPrompt`

---

# 五、与现有语义层边界怎么划

## 表语义
回答：
- 这张表是什么业务对象

## 列语义
回答：
- 这个字段是什么意思
- 是否可能成为 dimension / measure 候选

## 关系语义
回答：
- 表与表怎么 join

## 指标语义
回答：
- 这个业务指标怎么算
- 按什么聚合
- 用哪些过滤条件
- 依赖哪些表与字段

也就是说：

> 表 / 列 / 关系解决结构理解，metric 解决口径理解。

---

# 六、建议优先级

## P1 / P2 之间
我建议指标语义不要拖到太晚，因为：
- 你当前语义层已经有很好的结构骨架
- 再往前一步最自然的就是业务指标层

但它也不应早于：
- 测试基线
- 数据源路由
- 大数据分页
- 前端最小闭环

所以更适合在：
- Phase 3 或 Phase 4 初期启动

---

# 七、结论

如果 DataAgent 未来要从“结构查数助手”升级到“业务分析代理”，那么：

> 指标语义层一定要独立建模，而且应参考 dbt / MetricFlow 的建模思路，而不是继续往列语义表里堆字段。
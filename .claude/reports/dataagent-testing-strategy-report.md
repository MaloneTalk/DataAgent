# DataAgent 测试策略调研报告

生成时间：2026-05-10  
范围：测试金字塔、Mock 策略、集成测试范围、Testcontainers 引入建议

---

# 一、当前现状

当前项目在 `pom.xml` 中已经具备：
- `spring-boot-starter-test`

并且根据最新调研，仓库中已经开始出现：
- `SemanticSchemaServiceTest`
- `LogicalTableRelationServiceImplTest`

这说明测试层已经不再是绝对空白，但仍未形成完整测试体系。

---

# 二、推荐测试金字塔

## 1. 底层：单元测试（60%~70%）
适合覆盖：
- 语义 merge
- 可见性规则
- 参数校验
- 关系有效性判断
- 分页规则
- SQL 白名单/截断逻辑

## 2. 中层：Spring 集成测试（20%~30%）
适合覆盖：
- Service + Mapper + 数据库协作
- 事务与级联删除
- MyBatis SQL 行为
- SQL 执行与异常包装

## 3. 顶层：Controller / API 契约测试（10% 左右）
适合覆盖：
- MockMvc
- 参数校验
- 错误码
- JSON 结构
- SSE 响应基础行为

## 4. 少量端到端冒烟
适合覆盖：
- Agent 主链路能否从请求走到 tool 再返回结果
- 不追求大而全，主要做“链路没断”的验证

---

# 三、当前类与测试类型匹配建议

## 适合单测
- `SemanticVisibilitySupport`
- `SemanticSchemaService`
- `LogicalTableRelationServiceImpl`
- `SqlExecutor`

原因：
这些类有较多确定性分支逻辑，依赖可以 mock，回归价值高。

## 适合集成测试
- `DatasourceServiceImpl`
- `LogicalTableRelationMapper` / `TableInfoMapper` / `ColumnSemanticInfoMapper`
- `SqlExecutor` 对真实数据库行为

原因：
它们和数据库、事务、Mapper 的协作比纯逻辑更关键。

## 适合 MockMvc
- `AgentController`
- `TableSemanticController`
- `TableColumnSemanticController`
- `TableRelationSemanticController`
- `DatasourceController`

原因：
这些层更适合验证 HTTP 契约，而不是深层业务逻辑。

---

# 四、如果引入 Testcontainers，最值得先覆盖什么

## 1. `DatasourceServiceImpl.deleteById`
验证：
- 事务级联删除
- datasource / table_info / column_info / logical_table_relation 的协同删除

## 2. `SqlExecutor`
验证：
- 真实数据库 SELECT 执行
- 超时与异常包装
- 200 行截断逻辑

## 3. `LogicalTableRelationServiceImpl`
验证：
- 关系唯一约束
- create/update/delete
- 启停逻辑
- 分页查询

---

# 五、如果先引入 MockMvc，优先覆盖什么

## 1. 关系接口
- 创建
- 修改
- 启停
- 批量删除
- 参数错误

## 2. 表 / 列分页接口
- page/pageSize
- 参数校验
- 批量重置

## 3. Agent 接口
- `/chat`
- `/chat/stream`
- 默认 sessionId 行为
- 请求体校验

---

# 六、推荐引入顺序

## P0
1. 单元测试先补齐 `SemanticVisibilitySupport` / `SemanticSchemaService` / `LogicalTableRelationServiceImpl`
2. 再补 MockMvc
3. 再引入 Testcontainers 做高价值集成测试

## P1
4. 加 SSE 基础测试
5. 加 Agent 主链路冒烟测试

---

# 七、结论

DataAgent 的测试策略不应从最重的 E2E 开始，而应该：

> **先补语义和执行链路的高价值单测，再补数据库集成测试，最后补少量端到端冒烟。**

这样最符合你当前项目的阶段和收益。
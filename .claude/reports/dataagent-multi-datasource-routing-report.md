# DataAgent 多数据源路由调研报告

生成时间：2026-05-10  
范围：多数据源选择、会话绑定、工具链路由与最小落地方案

---

# 一、当前现状

当前系统并不是“按请求显式选库”，而是：

> **依赖 active datasource，全局取第一条**

具体表现：
- `ChatRequest` 当前只有 `sessionId` 和 `message`
- `AgentController` 不接收 `datasourceId`
- `AgentService` 的 sessionCache 只按 `sessionId` 缓存
- `GetTablesTool`、`GetTableSchemaTool`、`ExecuteSqlTool` 最终都依赖 `ActiveDatasourceSupport.getActiveDatasource()`
- 多个 active 数据源时，只是 warn 后取第一条

---

# 二、当前风险

## 1. 多 active 数据源误路由
如果多个 datasource 同时 active：
- 实际命中哪个库不确定
- 查询结果不可复现
- Agent 可能跨库误查

## 2. 会话与数据源没有绑定
当前 session 只绑定 `sessionId`，未绑定 `datasourceId`，意味着：
- 同一 session 切库时上下文可能串味
- 后续历史追溯也无法明确这次对话基于哪个库

## 3. 工具层路由不显式
工具看起来像“只关心表/SQL”，但其实其执行环境是隐式的，后续会成为平台化的核心风险。

---

# 三、行业与框架参考

## 1. Spring WebFlux / Reactor 官方实践
从 Spring Framework 与 Reactor 官方文档可以明确得到两个重要结论：

### 结论 A：响应式链路里不要把请求级上下文长期寄托在 ThreadLocal
Reactor 官方把 `Context` 视为 reactive 世界里替代 `ThreadLocal` 的机制，用于承载：
- traceId
- userId
- tenantId
- request metadata

### 结论 B：上下文应在链路末端统一 `contextWrite(...)`，在内部用 `deferContextual(...)` 读取
这非常适合当前 DataAgent 场景：
- Controller 入口写入 `datasourceId`
- Agent / tool / service 在链路内部读取 `datasourceId`
- 避免把 datasource 通过很多层显式传参到处串

这说明：
> **如果你后续要走显式 datasource 路由，优先应考虑“请求级 Reactor Context 路由”而不是继续沿用隐式 active datasource。**

## 2. 成熟数据平台的一般做法
知名数据系统通常不会采用“全局 active 第一条”的隐式路由方式，而是：
- 请求显式指定工作空间 / 数据源 / catalog
- 会话与目标数据源绑定
- 审计记录里带 datasource / project / tenant 标识

这和当前项目的缺口完全一致。

---

# 四、可选方案对比

## 方案 A：最小显式路由
### 做法
- `ChatRequest` 增加 `datasourceId`
- Controller 透传
- `AgentService` 会话 key 改为 `sessionId + datasourceId`
- Service / tool 继续显式传参

### 优点
- 改动路径清晰
- 易理解
- 不依赖响应式上下文机制

### 缺点
- 传参链会变长
- 后续如果工具和 service 继续增多，路由参数会继续扩散

---

## 方案 B：请求级路由上下文（推荐）
### 做法
- `ChatRequest` 增加 `datasourceId`
- 请求入口写入 `DatasourceRouteContext`
- 如果链路是响应式优先，使用 Reactor Context 承载
- tool / service 从上下文获取 datasourceId
- 会话 key 仍改为 `sessionId + datasourceId`

### 优点
- 路由集中
- 与 Spring WebFlux / Reactor 官方实践一致
- 后续审计、日志、traceId 也可同样走 Context

### 缺点
- 需要约束上下文使用方式
- 队伍需要接受 Context 方式，而不是到处显式传参

---

## 方案 C：继续沿用 active datasource，只增加约束
### 做法
- 限制系统任何时刻只能有一个 active datasource
- 通过管理规则保障唯一 active

### 优点
- 改动最小

### 缺点
- 本质上回避了多数据源产品能力
- 无法支撑真正的多租户 / 多项目 / 多环境查询

### 结论
不推荐作为长期方案。

---

# 五、推荐方案

## 推荐采用：方案 B（请求级路由上下文）

### 最小落地步骤
1. `ChatRequest` 增加 `datasourceId`
2. `AgentController` 透传 `datasourceId`
3. `AgentService` 的 session key 改为 `sessionId + datasourceId`
4. 新增 `DatasourceRouteContext`
5. `ActiveDatasourceSupport` 新增：
   - `requireDatasource(Integer datasourceId)`
   - `getCurrentOrActiveDatasource()`
6. tool / semantic service 统一优先走上下文 datasourceId，取不到再 fallback active

### 过渡策略
- 第一阶段保留 active datasource fallback
- 第二阶段在生产模式下禁用 fallback
- 第三阶段要求所有查询请求都必须显式带 datasourceId

---

# 六、为什么这是优先级很高的事

因为它不是普通功能增强，而是：
- 正确性问题
- 可复现性问题
- 多租户 / 多库能力问题
- 后续审计与治理的前提问题

如果这层不先定，后面：
- 审计日志
- 查询历史
- 长查询任务
- 前端切库体验
- 权限与配额模型

都会建立在不稳定的路由基础上。

---

# 七、建议优先级

## P0
- 在请求层引入 `datasourceId`
- 在会话层绑定 `sessionId + datasourceId`
- 在执行链路中引入显式 datasource 路由上下文

## P1
- 明确 active datasource 的唯一规则或直接退役“取第一个”的策略
- 在审计中记录 datasourceId
- 把 datasourceId 纳入结构化日志与 tracing 维度

---

# 八、结论

多数据源显式路由是当前 DataAgent 最值得优先收口的架构问题之一。

> 如果不先解决它，后续很多产品能力都会建立在不稳定的数据源选择机制上。
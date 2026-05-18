# DataAgent Agent 主链路调研报告

生成时间：2026-05-10  
范围：AgentService、AgentController、tool 层、会话与输出协议

---

# 一、当前已具备的主链路能力

## 1. Agent 调用闭环
当前主链路已经形成：
- `AgentController` 接收请求
- `AgentService` 创建 / 加载 ReActAgent
- Agent 调用工具
- 工具访问语义层 / 执行 SQL
- 返回同步结果或流式结果

## 2. 会话能力
当前已具备：
- `sessionId`
- 同步对话
- SSE 流式对话
- Session 清理
- MySQL Session 持久化

## 3. 工具链能力
当前工具链为：
- `get_tables`
- `get_table_schema`
- `execute_sql`

并且：
- 已统一 `ToolResult(success/data/error)` 协议
- `AgentService` prompt 已明确要求先看表、再看结构、再生成 SQL

## 4. 语义层接入
当前 Agent 已能看到：
- 表摘要
- 表详情
- 结构化列信息
- 结构化关系信息

这已经比“纯 schema 读表工具”强很多。

---

# 二、当前主链路仍缺什么

## 1. 缺显式数据源路由
当前主要依赖：
- `ActiveDatasourceSupport`
- 多个 active 时直接取第一个

这意味着：
- 多数据源场景不可控
- 会话无法显式绑定 datasource
- 结果可复现性差

## 2. 缺用户上下文与任务上下文
当前 `ChatRequest` 只看到：
- sessionId
- message

缺少：
- datasourceId
- 用户身份
- 模式约束
- 目标格式
- 业务上下文

## 3. 缺查询计划与可解释性
当前 Agent 只能输出结果，没有标准化能力输出：
- 为什么选这张表
- 为什么用这条关系
- SQL 是怎么一步步形成的

## 4. 缺异步任务模型
当前长查询只能：
- 同步等
- SSE 等

缺少：
- 后台任务化执行
- 查询取消
- 任务轮询
- 结果续取

## 5. 缺结果层产品化能力
缺：
- 大结果续取
- 下载导出
- 列视图切换
- 结果缓存
- 历史查询记录

---

# 三、当前最关键的架构风险

## 1. Agent 与数据源环境耦合过紧
风险点：
- Agent 会话记忆 + active datasource 是隐式耦合
- 多个 active 数据源时行为不稳定

## 2. 流式输出协议过弱
当前：
- `chat/stream` 返回 `Flux<String>`
- 前端拿不到事件类型

结果：
- 无法区分 reasoning/tool result/final
- 可观测性和 UI 表达力都受限

## 3. 主链路没有任务分层
当前还是：
- 会话 -> prompt -> tool -> SQL

还不是：
- 会话层
- 规划层
- 执行层
- 结果层

后续复杂度上来后扩展会吃力。

---

# 四、建议的演进方向

## P0
1. 显式 datasource 路由进入请求层
2. 明确会话与 datasource 绑定策略
3. 把流式输出升级为结构化事件流

## P1
4. 补查询计划 / SQL 解释能力
5. 增加异步长查询任务模型
6. 增加结果续取与导出能力

## P2
7. 增加查询历史 / 收藏 / 复用
8. 增加更细粒度任务型 Agent 能力

---

# 五、结论

当前 Agent 主链路已经能支撑“查数助手”能力，但距离“完善的 DataAgent 代理产品”还缺：
- 显式数据源路由
- 结构化事件流
- 异步任务模型
- 结果层产品能力
- 查询解释与审计能力
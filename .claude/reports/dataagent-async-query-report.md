# DataAgent 异步查询与结果层调研报告

生成时间：2026-05-10  
最后更新：2026-05-12  
范围：同步查询、SSE 流、长查询任务、结果续取与结果层设计  
补充参考：Apache Superset、Vanna.AI、DB-GPT

---

# 一、当前现状

当前查询链路主要有两种：

## 1. 同步聊天
- `/api/agent/chat`
- 返回 `Result<String>`

## 2. SSE 文本流
- `/api/agent/chat/stream`
- 返回 `Flux<String>`

同时：
- `ExecuteSqlTool` 仍是同步执行 SQL
- `SqlExecutor` 阻塞式 JDBC
- `QueryResult` 只承载一次性内存结果
- `MAX_ROWS=200`
- `QUERY_TIMEOUT_SECONDS=30`

---

# 二、当前结果层不足

## 1. 没有异步任务模型
当前缺少：
- taskId / jobId
- 查询状态机
- 查询取消
- 查询完成后结果继续读取

## 2. 结果对象过轻
当前 `QueryResult` 只有：
- columns
- rows
- totalRows
- truncated

缺少：
- 结果句柄
- page token / offset
- resultId
- 持久化位置
- 下载信息

## 3. SSE 只是即时文本流
当前 SSE 断开后：
- 无法恢复
- 无法继续读取历史事件
- 无法基于事件类型做结构化处理

---

# 三、行业参考

## 1. Apache Superset 的启发
Superset 的核心价值之一不在“能执行查询”，而在：
- 查询任务化
- 结果层独立化
- 前端围绕任务、结果、可视化来组织

这说明：
> **长查询与大结果不应一直挂在聊天流上，而应进入独立的结果层和任务层。**

## 2. 结构化结果对象是产品化前提
成熟的数据应用不会只返回：
- 一段文本

而会返回：
- 查询任务状态
- 结果元数据
- 可分页结果
- 下载能力
- 错误详情

这和当前 `Result<String>` / `Flux<String>` 的模式有明显差距。

## 3. 异步执行与结果续取通常拆层
成熟系统一般拆成：
- 提交任务
- 轮询状态
- 取结果页
- 取消任务
- 导出结果

这也是当前 DataAgent 最值得借鉴的产品化路径。

---

# 四、建议新增的核心对象

## 1. 查询任务对象
- `QueryTask`
- `QueryTaskStatus`
- `QueryTaskRequest`
- `QueryTaskDetailDTO`

## 2. 结果句柄对象
- `StoredQueryResult`
- `QueryResultHandle`
- `QueryResultPageDTO`

## 3. 错误对象
- `QueryTaskErrorDTO`

---

# 五、建议新增的核心服务

## 1. `QueryTaskService`
职责：
- submit
- poll
- cancel
- getResultPage

## 2. `QueryResultStore`
职责：
- save
- loadPage
- delete

## 3. `AsyncSqlExecutionDispatcher`
职责：
- 后台调度 SQL 任务
- 执行状态迁移

---

# 六、深度调研补充（2026-05-12）

## 1. Apache Superset 的 Query 模型启发

**参考位置**：`apache/superset/superset/models/sql_lab.py`

Superset 的 `Query` 模型不是临时内存对象，而是**持久化的查询历史实体**，核心字段包括：
- `client_id`：前端/调用侧唯一标识
- `database_id`、`user_id`：绑定数据库与用户
- `sql`、`executed_sql`：原始 SQL 与实际执行 SQL
- `status`：查询状态
- `results_key`：结果缓存键
- `rows`：结果行数
- `error_message`：错误信息
- `progress`：执行进度
- `start_time`、`end_time`：时间戳

### 对 DataAgent 的直接启发
当前 DataAgent 的查询对象太轻，只停留在 `QueryResult` 级别。后续至少应增加：
- `queryId`
- `datasourceId`
- `userId`
- `status`
- `resultsKey`
- `rowsAffected`
- `errorMessage`
- `progress`
- `startTime` / `endTime`

也就是说：
> **异步查询不是给现有结果对象补一个 taskId 就够了，而是要引入完整的查询历史实体。**

---

## 2. Superset 的异步事件流启发

**参考位置**：`apache/superset/superset/async_events/async_query_manager.py`

Superset 的异步查询管理并不是简单轮询，而是：
- 用 Redis Stream 保存事件
- 每个任务有 `channel_id`
- 事件包含：`status`、`errors`、`result_url`
- 支持用户专属 Stream 和全局 Stream

### 对 DataAgent 的直接启发
当前 `Flux<String>` 只能做即时文本展示，不能恢复历史，也不能按类型处理。

后续应把任务流升级为结构化事件，例如：
- `task_created`
- `task_running`
- `task_progress`
- `task_succeeded`
- `task_failed`
- `result_ready`

推荐事件结构：
```json
{
  "taskId": "q_20260512_abc123",
  "channelId": "ch_001",
  "status": "running",
  "progress": 45,
  "errors": [],
  "resultUrl": null,
  "updatedAt": "2026-05-12T15:20:00Z"
}
```

这比当前文本流更适合前端：
- 渲染进度条
- 断线后恢复
- 查询完成后跳转结果页

---

## 3. Vanna 的分析结果对象启发

**参考位置**：`vanna-ai/vanna-nextjs-flask/dependencies/base/index.py`

Vanna 的核心特点不是复杂封装，而是：
- `ask()` 返回 `SQL + DataFrame`
- 基于 `question + sql + df` 生成 `followupQuestions`
- 基于 `question + df` 生成 `summary`

### 对 DataAgent 的直接启发
DataAgent 不应该只返回：
- 一段文本
- 或一次性表格

而应该返回结构化分析结果：
- `sql`
- `resultPreview`
- `followupQuestions`
- `explanation`
- `chartSuggestion`
- `executionMetadata`

对应文档已新增：
- `../reference/analysis-result-schema.md`

也就是说：
> **结果层不是单纯存表格，而是要存“分析结果对象”。**

---

## 4. DB-GPT 的任务状态与输出抽象启发

**参考位置**：`eosphoros-ai/DB-GPT/packages/dbgpt-core/src/dbgpt/core/awel/task/base.py`

DB-GPT 的关键设计是：
- `TaskState`：`INIT` / `RUNNING` / `SUCCESS` / `FAILED`
- `TaskOutput`：统一抽象流式与非流式输出
- `TaskContext`：存储任务元数据与调用数据

### 对 DataAgent 的直接启发
当前 DataAgent 聊天层、执行层、结果层耦合过紧。

后续异步查询任务至少应具备：
- 明确的状态机
- 统一的结果输出抽象
- 元数据上下文（queryId、datasourceId、sessionId、userId）

这能避免后面新增：
- 导出
- 结果续取
- 取消任务
- 进度通知

时再大改核心结构。

---

# 七、建议新增的核心对象

## 1. 查询任务对象
- `QueryTask`
- `QueryTaskStatus`
- `QueryTaskRequest`
- `QueryTaskDetailDTO`

## 2. 结果句柄对象
- `StoredQueryResult`
- `QueryResultHandle`
- `QueryResultPageDTO`

## 3. 错误对象
- `QueryTaskErrorDTO`

## 4. 分析结果对象
- `AnalysisResult`
- `ResultPreview`
- `ChartSuggestion`
- `ExecutionMetadata`

---

# 八、建议新增的核心服务

## 1. `QueryTaskService`
职责：
- submit
- poll
- cancel
- getResultPage
- getHistory

## 2. `QueryResultStore`
职责：
- save
- loadPage
- delete
- export

## 3. `AsyncSqlExecutionDispatcher`
职责：
- 后台调度 SQL 任务
- 执行状态迁移
- 推送任务事件

## 4. `AnalysisResultBuilder`
职责：
- buildResultPreview
- recommendChart
- generateFollowupQuestions
- generateExplanation

---

# 九、接口建议

## 提交任务
- `POST /api/query-tasks`

## 查状态
- `GET /api/query-tasks/{taskId}`

## 取结果页
- `GET /api/query-tasks/{taskId}/result?page=1&pageSize=100`

## 取消任务
- `POST /api/query-tasks/{taskId}/cancel`

## 订阅任务事件（可选）
- `GET /api/query-tasks/{taskId}/stream`

---

# 十、与当前架构的关系

## 短期
当前可以先不推翻聊天接口，而是：
- 保留 `/chat` 与 `/chat/stream`
- 先增强结构化事件流
- 引入 `AnalysisResult` 作为新的结构化返回对象
- 再把“SQL 执行结果”逐步迁到结果层对象

## 中期
把长查询从聊天层剥离出来：
- 聊天层负责规划 / 解释
- 查询任务层负责执行 / 状态 / 结果
- 结果层负责缓存 / 分页 / 导出 / 复用

这样结构会更稳。

---

# 十一、建议优先级

## P0
- 定义 `AnalysisResult` 对象模型
- 定义查询历史实体
- 定义结果缓存键与缓存策略

## P1
- 先做结构化事件流
- 再定义任务对象与结果对象
- 做查询历史检索与重跑

## P2
- 做异步任务化
- 做结果续取 / 下载
- 做图表推荐与 follow-up questions

---

# 十二、相关文档

- 结果对象规范：`../reference/analysis-result-schema.md`
- 结果层产品化参考：`result-layer-benchmark.md`
- Agent 主链路调研：`dataagent-agent-backend-report.md`
- SQL 执行治理：`dataagent-execution-safety-report.md`

---

# 十三、结论

当前 Query 层还停留在“同步查数 + 文本流”的形态。

> 要成为完善的 DataAgent，必须把查询任务层和结果层从聊天层里独立出来。

深度调研后的核心判断是：
1. **先定义分析结果对象，再做异步任务化**
2. **先做查询历史与结果缓存，再做导出与可视化**
3. **不要把长查询一直挂在聊天流上，应进入独立任务层**

也就是说，结果层的下一步不是只补一个 `taskId`，而是：

> **引入查询历史实体 + 分析结果对象 + 结构化事件流 + 结果缓存机制，逐步形成完整的结果层。**
# DataAgent 可观测性与治理调研报告

生成时间：2026-05-10  
范围：日志、指标、追踪、审计、配置治理、平台化缺口

---

# 一、当前现状

当前项目具备：
- 基础日志输出
- Agent session MySQL 持久化
- 基础 CI
- 统一 HTTP / tool 返回结构

但未见：
- Actuator
- Micrometer
- Tracing / OpenTelemetry
- 结构化日志规范
- 查询审计体系
- secrets 管理
- profile 分层
- `src/test`

---

# 二、当前最重要的缺口

## 1. 缺统一观测基座
当前看不到：
- 接口耗时
- Agent 推理轮次
- tool 成功率
- SQL 执行耗时
- datasource 池状态
- schema 缓存命中率

## 2. 缺查询审计能力
当前无法标准化回答：
- 谁发起了什么请求
- 用了哪个 datasource
- 执行了什么 SQL
- 命中了哪些表/列/关系
- 查询是否截断 / 超时 / 失败

## 3. 缺配置治理
当前：
- 有明文数据库密码
- 无 profile 分层
- 无 secrets 管理
- 无配置聚合校验

## 4. 缺语义层治理能力
当前语义层仍缺：
- 审计日志
- 版本历史
- draft / published
- 软删除 / 回滚

---

# 三、行业参考

## 1. OpenTelemetry / 现代可观测性实践
行业成熟做法强调：
- 用 trace 把请求 -> agent -> tool -> SQL -> 结果串起来
- 用 metrics 看接口耗时、错误率、工具成功率、SQL 耗时
- 用统一 requestId / traceId 做日志关联

这对 DataAgent 非常适配，因为它天然是多阶段链路系统。

## 2. 元数据与治理平台实践
像 OpenMetadata / DataHub 这类平台说明：
- 治理不是“日志顺手补一张表”
- 而是：
  - glossary
  - lineage
  - audit
  - version
  - ownership
  - collaboration
  一整套系统

这对 DataAgent 的启发是：
> **语义层如果要走远，治理层必须单独规划，而不是等出问题再补。**

## 3. 平台产品的配置治理共识
成熟系统通常至少做到：
- 明文敏感信息不进仓库
- dev/test/prod profile 分层
- 运行参数外部化
- 配置变更可追踪

而当前项目明显还没走到这一步。

---

# 四、为什么观测与治理重要

对于 DataAgent，这不是锦上添花，而是产品化与平台化的底层条件：

- 没观测，Agent 出错无法定位是 prompt、tool、语义层还是 SQL 层
- 没审计，生产场景无法追踪“谁查了什么、为什么查错了”
- 没配置治理，多环境和生产发布风险极高
- 没语义治理，后续语义层会越来越难维护

---

# 五、建议优先补的能力

## P0
1. 结构化日志
2. requestId / sessionId / datasourceId / traceId 贯穿
3. SQL 执行审计日志
4. 配置脱敏与环境变量化

## P1
5. Actuator + 健康检查
6. Micrometer 指标
7. Agent / tool / SQL 分层指标

## P2
8. OpenTelemetry tracing
9. 语义层治理日志
10. 版本与发布态

---

# 六、建议关注的指标

## Agent 层
- 请求数
- 成功率
- 平均对话耗时
- 平均推理轮次
- 工具调用次数

## SQL 层
- SQL 执行耗时
- 截断率
- 超时率
- datasource 命中分布

## 语义层
- merge 耗时
- 可见表数量
- 可见列数量
- 逻辑关系有效率
- 分页缓存命中率（后续）

---

# 七、推荐的落地顺序

## 第一阶段：最低可用观测
- requestId / sessionId / datasourceId 打通
- 结构化日志
- SQL 执行审计日志

## 第二阶段：系统指标化
- Actuator
- Micrometer
- Agent / tool / SQL / datasource / semantic 分层指标

## 第三阶段：追踪与治理化
- OpenTelemetry tracing
- 语义层审计
- 版本 / draft / published

---

# 八、结论

当前 DataAgent 的最大平台化短板之一就是：

> 缺可观测性和治理层。

如果后续希望系统能稳定迭代、支持生产使用、支持多人协作，这一层必须尽早规划，而不是留到最后再补。
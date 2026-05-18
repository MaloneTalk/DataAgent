# DataAgent 整体缺口调研报告

生成时间：2026-05-10  
范围：基于当前代码，对 DataAgent 除语义层之外的整体能力缺口进行调研  
目的：回答“一个完善的 DataAgent 还缺什么”

---

# 一、结论摘要

如果把一个“完善的 DataAgent”定义为：

> 能稳定接入多数据源、理解语义层、生成与执行可控 SQL、对外提供可用的前端/接口体验，并具备测试、运维、观测、治理和扩展能力的完整数据代理系统。

那么当前项目已经具备：
- 基础 Agent 主链路
- 基础数据源管理
- 动态连接池
- 物理 schema 读取
- 只读 SQL 执行
- 语义层接入
- 基础 CI 与格式检查

但离“完善的 DataAgent”还缺 5 个层面：

1. **Agent 产品能力层**
2. **数据源与执行安全层**
3. **结果处理与大数据量能力层**
4. **前端与交互层**
5. **测试、观测、发布与治理层**

换句话说：

> 你现在已经有“一个能跑的语义化查数助手后端”，但还不是“一个完善的 DataAgent 产品”。

---

# 二、当前已具备的整体能力

## 1. Agent 主链路
当前已具备：
- 同步聊天接口
- SSE 流式聊天接口
- Session 清理接口
- ReActAgent + tool 调用闭环
- MySQL Session 持久化
- 统一 tool 协议 `ToolResult(success/data/error)`

关键位置：
- `agent/AgentService.java`
- `controller/AgentController.java`
- `dto/tool/ToolResult.java`

## 2. 数据源与 schema
当前已具备：
- 数据源 CRUD
- 动态创建数据源连接池
- JDBC 元数据读取：表 / 列 / 主键 / 外键
- 活动数据源选择器

关键位置：
- `service/impl/DatasourceServiceImpl.java`
- `controller/DatasourceController.java`
- `agent/datasource/DynamicDataSourceManager.java`
- `agent/datasource/SchemaReader.java`
- `service/ActiveDatasourceSupport.java`

## 3. SQL 执行
当前已具备：
- 仅允许 SELECT
- 查询超时
- 最大结果行数截断
- 基础正则安全校验

关键位置：
- `agent/datasource/SqlExecutor.java`

## 4. 语义层接入
当前已具备：
- 表 / 列 / 关系三层语义
- 物理与逻辑关系 merge
- `get_tables` / `get_table_schema` 结构化输出

详见：
- `reports/semantic-layer-full-report.md`

## 5. 基础工程能力
当前已具备：
- Maven 构建
- Spotless 校验
- GitHub Actions `mvn verify`

关键位置：
- `data-agent-backend/pom.xml`
- `.github/workflows/maven.yml`

---

# 三、一个完善的 DataAgent 还缺什么

# 1. Agent 产品能力层

当前 Agent 更像：
- 单轮 / 多轮查数助手
- 依赖隐式 active datasource
- 依赖固定工具流程

还缺：
- 显式数据源选择与会话绑定
- 用户上下文 / 业务上下文输入
- 查询计划展示
- SQL 解释与结果解释
- 大结果续取
- 异步长查询任务
- 查询历史与收藏
- 失败重试策略
- 结构化事件流输出

这是从“技术 demo”走向“产品”的核心缺口。

---

# 2. 数据源与执行安全层

当前已经有最基础的只读控制，但还缺：
- 数据源连通性测试
- 数据源健康检查
- 密码脱敏 / 加密存储
- 更强 SQL 校验（AST 级别）
- 多租户隔离
- 权限控制
- 查询审计
- 限流与配额
- 显式数据源路由

如果没有这些，系统适合开发与演示，不适合严肃生产使用。

---

# 3. 结果处理与大数据量能力层

当前结果层面还缺：
- 真正的数据层分页
- 大表 / 大 schema 的缓存策略
- 异步查询与轮询结果
- 结果集下载
- 列裁剪与行裁剪策略
- SQL 成本保护
- 结果缓存

这是当前性能和稳定性的关键瓶颈层。

---

# 4. 前端与交互层

当前前端在仓库里基本未落地。还缺：
- 聊天页面
- SSE 流式渲染
- tool result 可视化
- 语义管理界面
- 数据源管理界面
- 关系维护界面
- 候选表 / 字段选择器
- 查询历史与错误提示

没有这一层，后端再完整，也还不是完整产品。

---

# 5. 测试、观测、发布与治理层

当前这是最弱的一层，缺：
- `src/test`
- 单元测试
- 集成测试
- 数据库相关测试
- metrics / tracing / actuator
- 结构化日志
- 环境分层配置
- secrets 管理
- 部署链路
- 发布回滚能力

这一层决定系统是否可持续维护。

---

# 四、优先级建议

## P0：必须补
1. 测试体系
2. 数据源安全与配置治理
3. 大数据量分页 / 缓存 / 查询保护
4. Agent 显式数据源路由

## P1：强烈建议补
5. 前端真实工程与联调
6. 结构化流式事件输出
7. 查询审计与可观测性

## P2：产品化增强
8. 异步长查询
9. 查询历史 / 收藏
10. 指标语义与分析能力增强

---

# 五、推荐阅读

- Agent 主链路详报：`dataagent-agent-backend-report.md`
- 数据源与执行链详报：`dataagent-datasource-execution-report.md`
- 前端 / 测试 / 运维详报：`dataagent-platform-readiness-report.md`

---

# 六、当前最该做的三件事

如果现在只能选三件，建议优先级：

## 1. 先补测试
**原因**：当前所有核心链路都缺自动化保护，这是最大的交付风险。

## 2. 先补大数据量分页与缓存优化
**原因**：当前已经明确出现”分页只缩小返回体，没有降低后端成本”的问题，这会直接影响后续稳定性。

## 3. 先补显式 datasource 路由
**原因**：当前多数据源场景依赖 active datasource 隐式选择，是一个结构性风险。

---

# 七、结论

当前项目已经具备”语义化查数助手”的核心后端能力，但要成为”完善的 DataAgent”，还必须补齐：

- 产品化 Agent 能力
- 数据源与执行安全
- 大数据量处理
- 前端交互
- 测试与运维治理

也就是说：

> 现在离”完整语义层”不远了，但离”完整 DataAgent 产品”还有一层明显的产品与平台能力差距。

**总体判断**：

> **这个项目值得继续做，而且不是从零重建，而是基于现有语义层和 Agent 主链路继续产品化。**

当前最优策略不是继续零散补功能，而是：

> **先稳住底盘，再补执行安全，再补前端与产品层，最后进入高级语义与治理层。**

这样投入最稳，复用也最高。
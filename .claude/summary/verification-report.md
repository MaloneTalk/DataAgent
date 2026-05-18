# 当前代码审查快照

生成时间：2026-05-10  
**最后更新：2026-05-17**  
审查范围：DataAgent 核心链路与语义层  
审查方式：静态代码审查

> 说明：本文件只保留**当前代码状态的核心评估结论**。详细事实参见 `../reference/dataagent-architecture-baseline.md` 和 `../reference/semantic-layer-baseline.md`。

---

## 一、当前已闭环的核心能力

### 1. 语义层三层结构
- ✅ 表语义（domain, description, visibility）+ 分页/搜索/排序/批量重置
- ✅ 列语义（description, visibility）+ 分页/搜索/排序/批量重置
- ✅ 关系语义（logical relation + physical relation merge）+ 分页/搜索/排序/批量删除

### 2. 语义合并引擎
- ✅ `SemanticCatalog`：请求级 `CatalogContext`，缓存合并结果
- ✅ `SemanticVisibilitySupport`：物理 + 语义 merge，可见性判断
- ✅ 复合外键聚合（`SchemaReader.ImportedKeyAggregate`）
- ✅ `SchemaReader` TTL 60s 内存缓存（表/列/关系三级）

### 3. Agent 工具链
- ✅ `get_tables` → 可见表分页列表（直接依赖 `TableSemanticService`）
- ✅ `get_table_schema` → 表结构化 schema（列、关系各自分页，直接依赖 `TableSemanticService`）
- ✅ `execute_sql` → 只读 SQL 执行
- ✅ 统一 `ToolResult(success/data/error)` 协议

### 4. 数据源与执行
- ✅ 数据源 CRUD
- ✅ 动态连接池（按需创建 Hikari 池）
- ✅ 物理 schema 读取（含复合外键）
- ✅ 只读 SQL 执行（最多 200 行，超时 30 秒）

### 5. 会话管理
- ✅ 同步对话 / SSE 流式对话
- ✅ MySQL Session 持久化（`data-agent:` 命名空间）
- ✅ Session 清理（单个 + 全部）

### 6. 包结构
- ✅ 语义层服务全部迁入 `service.semantic` 包（含 `impl` 子包）
- ✅ `LogicalTableRelationSupport` / `ActiveDatasourceSupport` / `ActiveDatasourceLockManager` 保留在 `service` 包

---

## 二、当前最紧迫的问题

### P0 - 必须立即解决
1. **缺测试基线**
   - `src/test` 目录不存在
   - 核心链路缺自动化回归保护

2. **缺 migration / 升级方案**
   - `sql/data_source.sql` 适合初始化，但不适合已有环境平滑升级

3. **多数据源路由不明确**
   - 多个 active 时直接取第一个
   - 存在跨库误查风险

### P1 - 强烈建议解决
4. **Agent 流式输出仍是纯文本流**
   - `chat/stream` 返回 `Flux<String>`
   - 前端无法区分 reasoning / tool result / final answer

5. **前端接入状态不明确**
   - 当前仓库未看到实际前端调用代码

6. **数据源安全治理缺失**
   - 明文密码，缺 secrets 管理，缺脱敏与审计

---

## 三、综合评分

### 技术维度
- 代码质量：**95/100**（分页/搜索/排序/合并引擎完成，设计清晰）
- 测试覆盖：**55/100**（`src/test` 不存在，无变化）
- 规范遵循：**92/100**（包结构已收口，Spotless 有效）

### 战略维度
- 需求匹配：**96/100**（语义层三层全覆盖，接口完整）
- 架构一致：**93/100**（`SemanticCatalog` 引入后层次更清晰）
- 风险评估：**86/100**（datasource 路由和测试覆盖仍是主要风险）

### 综合评分
**92/100**

### 结论
**通过。语义层管理能力已完整，但仍不建议在缺测试与迁移方案的情况下直接作为生产稳定基线。**

---

## 四、下一步建议

参见 `../planning/phase1-progress.md` 的当前最推荐开工顺序：
1. 测试基线（A1/A2/A3）
2. 迁移与升级方案（B1/B2）
3. 显式 datasource 路由（F1）

# Phase 1 进度追踪

生成时间：2026-05-10  
**最后更新：2026-05-17**  
范围：DataAgent / 语义层 Phase 1（收口现有骨架）  
说明：本文件只做简洁跟踪，帮助快速判断"做到哪了、下一步做什么"。

---

## 总体状态

- **Phase 1 目标**：把当前表 / 列 / 关系三层语义，以及 DataAgent 后端基础骨架做稳、做全、做易维护
- **当前整体状态**：语义层分页/搜索/排序/批量操作已全部落地，包结构已收口；测试与迁移方案仍未启动
- **建议下一步**：测试基线（A1/A2/A3）和迁移方案（B1/B2）

---

## 任务清单

| 任务 | 状态 | 说明 | 相关文件 |
|---|---|---|---|
| A1. `SemanticVisibilitySupport` 测试 | 未开始 | 需要为表/列 merge 与可见性规则建立回归测试 | `service/semantic/SemanticVisibilitySupport.java` |
| A2. `TableSemanticService` / `ColumnSemanticService` 测试 | 未开始 | 需要覆盖分页/搜索/排序、merge 与 `getTableSchema` 输出 | `service/semantic/impl/TableSemanticServiceImpl.java`、`ColumnSemanticServiceImpl.java` |
| A3. `LogicalTableRelationServiceImpl` 测试 | 未开始 | 需要覆盖关系创建/修改/启停/有效性判定 | `service/semantic/impl/LogicalTableRelationServiceImpl.java` |
| B1. 旧库升级 SQL / migration | 未开始 | 当前只有初始化 SQL，缺已有环境升级路径 | `sql/data_source.sql` |
| B2. 升级说明文档 | 未开始 | 需要明确新环境初始化与旧环境升级步骤 | `sql/`、`.claude/` |
| C1. 表语义分页/搜索/排序 | **完成** | 物理表+当前页语义回填，已支持 `keywordPrefix`、`sortOrder`、`datasourceId` 过滤 | `controller/TableSemanticController.java`、`service/semantic/impl/TableSemanticServiceImpl.java` |
| C2. 列语义分页/搜索/排序 | **完成** | 物理列+当前页语义回填，已支持 `keywordPrefix`、`sortOrder`、`datasourceId` 过滤 | `controller/TableColumnSemanticController.java`、`service/semantic/impl/ColumnSemanticServiceImpl.java` |
| C3. 关系语义分页/搜索/排序 | **完成** | 已支持 `keywordPrefix`、`enabled`、`sortOrder` 过滤 | `controller/TableRelationSemanticController.java`、`service/semantic/impl/LogicalTableRelationServiceImpl.java` |
| C4. 批量启停/批量操作 | **完成** | 表/列批量重置已具备，关系批量删除已具备；批量启停（关系）待定 | `controller/TableSemanticController.java`、`TableColumnSemanticController.java`、`TableRelationSemanticController.java` |
| D1. 统一实现层包结构 | **完成** | 所有语义层服务已迁入 `service.semantic`（含 `impl` 子包） | `service/semantic/**` |
| D2. 文档同步 | **进行中** | 本次更新已同步 `reference/`、`summary/`、`planning/` | `.claude/**/*.md` |
| E1. Agent SSE 结构化事件流预研 | 未开始 | 当前 `chat/stream` 仍是 `Flux<String>` 文本流 | `agent/AgentService.java`、`controller/AgentController.java` |
| F1. 显式 datasource 路由方案 | 未开始 | 当前仍依赖 active datasource 取第一个，存在结构性风险 | `service/ActiveDatasourceSupport.java`、`agent/AgentService.java` |
| F2. 数据源健康检查与连通性测试 | 未开始 | 当前数据源链路缺健康检查与测试接口 | `service/impl/DatasourceServiceImpl.java`、`controller/DatasourceController.java` |
| F3. 数据源安全治理 | 未开始 | 明文密码、缺 secrets 管理、缺脱敏与审计 | `application.properties`、`entity/Datasource.java` |

---

## 已完成事项汇总（2026-05-17 更新）

### 1. 文档体系
- 全套 `.claude/` 文档已建立并随代码同步更新

### 2. 语义层能力（本次确认完成）
- 表/列/关系三层语义分页 + 搜索 + 排序
- 表/列批量重置，关系批量删除
- 包结构统一收入 `service.semantic`

### 3. 语义合并引擎（新增）
- `SemanticCatalog`（请求级合并缓存）
- `SchemaReader` TTL 60s 三级缓存 + `invalidateCache`
- 复合外键聚合（`ImportedKeyAggregate`）

---

## 当前最推荐开工顺序

1. **A1 / A2 / A3：测试基线**
2. **B1 / B2：迁移与升级方案**
3. **F1 / F2 / F3：datasource 路由、安全与健康检查**
4. **E1：SSE 结构化事件流预研**

---

## 使用方式

- 某个任务开始做了 → 改成"进行中"
- 某个任务做完了 → 改成"完成"
- 代码事实变化了 → 同步检查 `../governance/document-update-rules.md` 指向的文档

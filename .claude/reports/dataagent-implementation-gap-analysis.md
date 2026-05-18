# DataAgent 实施差距分析报告

生成时间：2026-05-17  
基于：当前代码分析 + 五份调研报告（AgentScope-Java、语义层、路由/SSE、安全执行、测试体系）

---

## 一、Agent 主链路差距分析

### 1.1 当前实现

```
AgentController
  ├── POST /api/agent/chat          → AgentService.chat()
  ├── POST /api/agent/chat/stream   → AgentService.chatStream()
  └── DELETE /api/agent/session     → AgentService.clearSession()

AgentService
  ├── 单一 Toolkit（全局共享）
  ├── ReActAgent + InMemoryMemory
  ├── MysqlSession 持久化
  └── StreamOptions(REASONING, TOOL_RESULT)
```

### 1.2 差距清单

| 能力 | 当前状态 | 目标状态 | 差距 |
|---|---|---|---|
| **多数据源路由** | ❌ 单一 Toolkit | ToolGroup 按数据源分组 | 需重构 init() |
| **SSE 事件结构** | 原始文本流 | AG-UI Protocol 19 种事件 | 需引入 AguiAgentAdapter |
| **数据源上下文** | ❌ 无 | 请求级 datasourceId 注入 | 需改造 ChatRequest |
| **工具审计** | ❌ 无 | Chunk Callback 记录 | 需添加 setChunkCallback |
| **System Prompt** | 硬编码 | 动态加载语义规则 | 需模板化 |

---

## 二、语义层差距分析

### 2.1 实体字段差距

| 实体 | 缺失字段 | 优先级 |
|---|---|---|
| `ColumnSemanticInfo` | display_name, semantic_type, time_role, enum_schema, example_values | **P0** |
| `TableInfo` | table_role, preferred_time_column, partition_column, estimated_row_count | **P1** |
| `LogicalTableRelation` | is_preferred, priority, join_hint | **P1** |
| 全部三表 | status, is_deleted | P2 |

### 2.2 工具输出差距

| 工具 | 当前输出 | 目标输出 |
|---|---|---|
| `GetTablesTool` | name, description | + tableRole, estimatedRowCount |
| `GetTableSchemaTool` | columns(name,type,pk,nullable,default,desc) | + displayName, semanticType, timeRole, enumValues |
| `GetTableSchemaTool` | relations(type,source,target,desc) | + isPreferred, joinHint |
| **GetMetricsTool** | ❌ 不存在 | metricName, sqlExpression, dimensions |

---

## 三、安全与审计差距

| 能力 | 当前状态 | 目标状态 |
|---|---|---|
| SQL 白名单 | ❌ 无 | SELECT-only 校验 |
| 执行超时 | ❌ 无 | 可配置 timeout |
| 结果行数限制 | ❌ 无 | maxRows 参数 |
| 审计日志 | ❌ 无 | semantic_audit_log 表 |
| 密码加密 | 明文存储 | AES/Jasypt 加密 |

---

## 四、测试体系差距

| 层级 | 当前状态 | 目标状态 |
|---|---|---|
| 单元测试 | ❌ 无 | Service/Tool 层 Mockito 测试 |
| 集成测试 | ❌ 无 | H2 内存库 + @SpringBootTest |
| Agent 测试 | ❌ 无 | 工具链 Mock + 断言 |
| CI 流水线 | ❌ 无 | GitHub Actions mvn test |

---

## 五、优先级排序实施计划

### Phase 1：核心能力补齐（2 周）

**Week 1：语义层 P0**
- [ ] DDL：column_info 加 5 列
- [ ] Entity：ColumnSemanticInfo 加字段
- [ ] DTO：ColumnSemanticPrompt 扩展
- [ ] Service：SemanticCatalog.resolveColumn() 解析新字段
- [ ] API：UpdateColumnSemanticRequest 扩展

**Week 2：Agent 多数据源**
- [ ] DTO：ChatRequest 加 datasourceId
- [ ] Service：AgentService.init() 改造为 ToolGroup 模式
- [ ] Service：请求级 toolkit.setActiveGroups()
- [ ] Controller：chatStream 返回 AG-UI 事件

### Phase 2：推理增强（1 周）

- [ ] DDL：table_info 加 4 列
- [ ] DDL：logical_table_relation 加 3 列
- [ ] DTO：TableSemanticPrompt/RelationSemanticPrompt 扩展
- [ ] Tool：GetTablesTool 输出 tableRole
- [ ] Tool：GetTableSchemaTool 输出 joinHint

### Phase 3：指标层（1 周）

- [ ] DDL：metric_definition 新表
- [ ] Entity：MetricDefinition
- [ ] Mapper：MetricDefinitionMapper
- [ ] Service：MetricDefinitionService
- [ ] Tool：GetMetricsTool 新建

### Phase 4：安全与治理（1 周）

- [ ] SQL 白名单校验（ExecuteSqlTool）
- [ ] 执行超时 + maxRows
- [ ] DDL：三表加 status/is_deleted
- [ ] DDL：semantic_audit_log 新表
- [ ] AOP：SemanticAuditAspect

### Phase 5：测试体系（1 周）

- [ ] pom.xml 加测试依赖
- [ ] src/test 目录结构
- [ ] SemanticCatalogTest
- [ ] GetTableSchemaToolTest
- [ ] GitHub Actions CI

---

## 六、快速启动任务（可立即开工）

### 6.1 语义层 Phase 1 DDL

```sql
ALTER TABLE column_info
  ADD COLUMN display_name   VARCHAR(128)  NULL,
  ADD COLUMN semantic_type  VARCHAR(32)   NULL,
  ADD COLUMN time_role      VARCHAR(32)   NULL,
  ADD COLUMN enum_schema    VARCHAR(2000) NULL,
  ADD COLUMN example_values VARCHAR(500)  NULL;
```

### 6.2 ChatRequest 扩展

```java
// dto/ChatRequest.java
public record ChatRequest(
    @NotBlank String sessionId,
    @NotBlank String message,
    Integer datasourceId  // 新增，可选
) {}
```

### 6.3 ToolGroup 初始化骨架

```java
// AgentService.init() 改造
@PostConstruct
public void init() {
    this.toolkit = new Toolkit();
    
    // 注册全局工具
    toolkit.registerTool(getTablesTool);
    toolkit.registerTool(getTableSchemaTool);
    toolkit.registerTool(executeSqlTool);
    
    // TODO: 按数据源创建 ToolGroup
    // for (Datasource ds : datasourceService.findAll()) {
    //     toolkit.createToolGroup("ds-" + ds.getId(), ds.getName(), false);
    // }
}
```

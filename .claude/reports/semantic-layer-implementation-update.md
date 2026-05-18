# 语义层实施更新报告

生成时间：2026-05-17  
基于：`semantic-layer-implementation-research.md` + AgentScope-Java 源码验证结果 + 当前代码状态分析

---

## 一、当前实现状态分析

### 1.1 实体字段现状

| 实体 | 当前字段 | 缺失字段（调研报告规划） |
|---|---|---|
| `TableInfo` | id, tableName, tableDescription, domain, datasourceId, isActive, isVisible, createTime, updateTime | **table_role**, **preferred_time_column**, **partition_column**, **estimated_row_count**, **recommended_filters**, status, is_deleted |
| `ColumnSemanticInfo` | id, datasourceId, tableName, columnName, columnDescription, isActive, isVisible, createTime, updateTime | **display_name**, **semantic_type**, **time_role**, **enum_schema**, **example_values**, status, is_deleted |
| `LogicalTableRelation` | id, datasourceId, sourceTableName, sourceColumnNamesJson, targetTableName, targetColumnNamesJson, relationType, description, isEnabled, createTime, updateTime | **is_preferred**, **priority**, **join_hint**, **usage_context**, status, is_deleted |

### 1.2 Agent 工具现状

| 工具 | 当前能力 | 缺失能力 |
|---|---|---|
| `GetTablesTool` | 返回表名+描述列表 | 缺少 table_role、estimated_row_count |
| `GetTableSchemaTool` | 返回列信息+关系，支持分页 | 缺少 semantic_type、time_role、enum_values、join_hint |
| `ExecuteSqlTool` | 执行 SQL 返回结果 | 无变化需求 |
| **GetMetricsTool** | ❌ 不存在 | 需新建，返回指标定义 |

### 1.3 服务层现状

`service/semantic/` 包已有完整的语义服务架构：
- `SemanticCatalog` - 语义目录聚合
- `SemanticVisibilitySupport` - 可见性控制
- `TableSemanticService` / `ColumnSemanticService` - 表/列语义服务
- `SemanticPageService` - 分页支持

**结论**：服务层架构完善，只需扩展实体字段和 Prompt 输出。

---

## 二、与 AgentScope-Java 能力的结合点

### 2.1 ToolGroup + 语义层 = 智能工具路由

**场景**：不同数据源的语义配置不同，Agent 需要根据数据源自动加载对应的语义上下文。

**实现方案**（结合已验证的 ToolGroup API）：

```java
// AgentService.java
@PostConstruct
public void init() {
    for (Datasource ds : datasourceService.findAll()) {
        String groupName = "ds-" + ds.getId();
        toolkit.createToolGroup(groupName, ds.getName(), false);
        
        // 每个数据源的工具绑定该数据源的语义服务
        SemanticCatalog.CatalogContext ctx = semanticCatalog.createContext(ds);
        
        toolkit.registration()
            .tool(new GetTablesTool(ctx))      // 注入语义上下文
            .tool(new GetTableSchemaTool(ctx))
            .tool(new GetMetricsTool(ctx))     // 新增
            .group(groupName)
            .apply();
    }
}
```

### 2.2 AG-UI Protocol + 语义层 = 结构化推理展示

**场景**：前端需要展示 Agent 的推理过程，包括"正在查询表结构"、"发现 3 个相关指标"等语义层交互。

**实现方案**（结合已验证的 AguiEvent）：

```java
// 在 AguiAgentAdapter 的事件流中，工具调用会自动生成：
// - ToolCallStart: {"toolCallName": "get_table_schema", "toolCallId": "xxx"}
// - ToolCallResult: {"content": "{...schema json...}"}

// 前端可按工具名分类展示：
switch (event.toolCallName) {
    case "get_tables" -> showTableListPanel(event);
    case "get_table_schema" -> showSchemaPanel(event);
    case "get_metrics" -> showMetricsPanel(event);  // 新增
    case "execute_sql" -> showSqlResultPanel(event);
}
```

### 2.3 Chunk Callback + 语义层 = 语义使用审计

**场景**：追踪哪些语义配置被 Agent 实际使用，用于优化语义覆盖率。

**实现方案**（结合已验证的 Chunk Callback）：

```java
toolkit.setChunkCallback((toolUse, result) -> {
    if ("get_table_schema".equals(toolUse.getName())) {
        String tableName = (String) toolUse.getInput().get("table_name");
        semanticUsageService.recordTableAccess(datasourceId, tableName);
    }
    if ("get_metrics".equals(toolUse.getName())) {
        semanticUsageService.recordMetricAccess(datasourceId);
    }
});
```

---

## 三、分阶段实施计划

### Phase 1：高级字段语义（P0，3-5 天）

**目标**：让 LLM 能区分维度/指标/时间列，提升 SQL 生成准确率。

#### 3.1.1 数据库变更

```sql
-- column_info 表加列
ALTER TABLE column_info
  ADD COLUMN display_name   VARCHAR(128)  NULL  COMMENT '业务展示名（中文）',
  ADD COLUMN semantic_type  VARCHAR(32)   NULL  COMMENT 'DIMENSION|MEASURE|TIME|IDENTIFIER|ENUM|FOREIGN_KEY|STATUS',
  ADD COLUMN time_role      VARCHAR(32)   NULL  COMMENT 'EVENT_TIME|PROCESS_TIME|REPORT_DATE|FISCAL_PERIOD|EXPIRY_TIME|PARTITION_KEY',
  ADD COLUMN enum_schema    VARCHAR(2000) NULL  COMMENT '枚举定义 JSON',
  ADD COLUMN example_values VARCHAR(500)  NULL  COMMENT '示例值 JSON 数组';
```

#### 3.1.2 实体变更

```java
// ColumnSemanticInfo.java 新增字段
private String displayName;
private String semanticType;   // DIMENSION|MEASURE|TIME|IDENTIFIER|ENUM|FOREIGN_KEY|STATUS
private String timeRole;       // EVENT_TIME|PROCESS_TIME|...
private String enumSchema;     // JSON string
private String exampleValues;  // JSON array string
```

#### 3.1.3 Prompt 输出变更

```java
// dto/semantic/ColumnSemanticPrompt.java 扩展
public record ColumnSemanticPrompt(
    String name,
    String type,
    Boolean primaryKey,
    Boolean nullable,
    String defaultValue,
    String description,
    // 新增字段
    @JsonInclude(JsonInclude.Include.NON_NULL) String displayName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String semanticType,
    @JsonInclude(JsonInclude.Include.NON_NULL) String timeRole,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<EnumValueEntry> enumValues,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<String> exampleValues
) {}

public record EnumValueEntry(String code, String label) {}
```

#### 3.1.4 SemanticCatalog 变更

```java
// SemanticCatalog.resolveColumn() 方法扩展
public ResolvedColumn resolveColumn(ColumnMergeSnapshot snapshot) {
    // ... 现有逻辑
    
    // 新增：解析 enum_schema JSON
    List<EnumValueEntry> enumValues = null;
    if (semanticColumn != null && semanticColumn.getEnumSchema() != null) {
        enumValues = parseEnumSchema(semanticColumn.getEnumSchema());
    }
    
    // 新增：解析 example_values JSON
    List<String> exampleValues = null;
    if (semanticColumn != null && semanticColumn.getExampleValues() != null) {
        exampleValues = parseExampleValues(semanticColumn.getExampleValues());
    }
    
    return new ResolvedColumn(
        // ... 现有字段
        semanticColumn != null ? semanticColumn.getDisplayName() : null,
        semanticColumn != null ? semanticColumn.getSemanticType() : null,
        semanticColumn != null ? semanticColumn.getTimeRole() : null,
        enumValues,
        exampleValues
    );
}
```

#### 3.1.5 管理接口变更

```java
// TableColumnSemanticController.java 扩展
// PUT /api/tableinfo/semantic/column/{id} 请求体增加新字段
public record UpdateColumnSemanticRequest(
    String columnDescription,
    Boolean isVisible,
    // 新增
    String displayName,
    String semanticType,
    String timeRole,
    String enumSchema,
    String exampleValues
) {}
```

---

### Phase 2：分析推理增强（P1，3-5 天）

**目标**：让 LLM 能识别事实表/维度表，自动选择正确的 JOIN 路径。

#### 3.2.1 数据库变更

```sql
-- table_info 表加列
ALTER TABLE table_info
  ADD COLUMN table_role              VARCHAR(32)  NULL  COMMENT 'FACT|DIMENSION|BRIDGE|AGGREGATE|LOG|CONFIG',
  ADD COLUMN preferred_time_column   VARCHAR(128) NULL  COMMENT '首选时间过滤列',
  ADD COLUMN partition_column        VARCHAR(128) NULL  COMMENT '分区键列名',
  ADD COLUMN estimated_row_count     VARCHAR(16)  NULL  COMMENT '<1K|<1M|<100M|>100M';

-- logical_table_relation 表加列
ALTER TABLE logical_table_relation
  ADD COLUMN is_preferred  TINYINT(1)   NOT NULL DEFAULT 0  COMMENT '首选 JOIN 路径',
  ADD COLUMN priority      TINYINT      NOT NULL DEFAULT 0  COMMENT '0普通/1首选/2强制',
  ADD COLUMN join_hint     VARCHAR(512) NULL               COMMENT '给 LLM 的使用场景说明';
```

#### 3.2.2 Prompt 输出变更

```java
// dto/semantic/TableSemanticPrompt.java 扩展
public record TableSemanticPrompt(
    String name,
    String description,
    // 新增
    @JsonInclude(JsonInclude.Include.NON_NULL) String tableRole,
    @JsonInclude(JsonInclude.Include.NON_NULL) String preferredTimeColumn,
    @JsonInclude(JsonInclude.Include.NON_NULL) String partitionColumn,
    @JsonInclude(JsonInclude.Include.NON_NULL) String estimatedRowCount
) {}

// dto/semantic/RelationSemanticPrompt.java 扩展
public record RelationSemanticPrompt(
    String relationType,
    String source,
    String sourceTableName,
    List<String> sourceColumnNames,
    String targetTableName,
    List<String> targetColumnNames,
    String description,
    // 新增
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isPreferred,
    @JsonInclude(JsonInclude.Include.NON_NULL) String joinHint
) {}
```

---

### Phase 3：指标语义层（P1，5-7 天）

**目标**：让 LLM 能直接使用预定义的业务指标，避免聚合逻辑错误。

#### 3.3.1 新建实体和表

```sql
CREATE TABLE metric_definition (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    datasource_id    INT          NOT NULL,
    metric_name      VARCHAR(128) NOT NULL,
    display_name     VARCHAR(128) NOT NULL,
    description      TEXT,
    metric_type      VARCHAR(32)  NOT NULL  COMMENT 'SIMPLE|RATIO|DERIVED|CUMULATIVE',
    source_table     VARCHAR(128) NOT NULL,
    expression       VARCHAR(500) NOT NULL,
    aggregation      VARCHAR(32)  NOT NULL  COMMENT 'SUM|COUNT|COUNT_DISTINCT|AVG|MAX|MIN|RATIO|CUSTOM',
    filter_condition VARCHAR(500),
    dimension_columns VARCHAR(500),
    time_column      VARCHAR(128),
    is_enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    create_time      DATETIME     NOT NULL,
    update_time      DATETIME     NOT NULL,
    UNIQUE KEY uk_metric (datasource_id, metric_name)
) ENGINE=InnoDB;
```

#### 3.3.2 新建 GetMetricsTool

```java
@Component
public class GetMetricsTool {
    
    private final MetricDefinitionService metricService;
    
    @Tool(
        name = "get_metrics",
        description = "获取当前数据源已定义的业务指标列表。返回指标名称、展示名、" +
                      "聚合方式、SQL 表达式片段、可关联维度。生成包含指标计算的 SQL 时应优先调用此工具。"
    )
    public ToolResult<List<MetricPromptItem>> getMetrics(
        @ToolParam(name = "table_name", description = "可选，按主表名过滤") String tableName
    ) {
        List<MetricDefinition> metrics = metricService.findByDatasource(datasourceId, tableName);
        List<MetricPromptItem> items = metrics.stream()
            .map(this::toPromptItem)
            .toList();
        return ToolResult.success(items);
    }
}
```

---

### Phase 4：治理能力（P2，3-5 天）

**目标**：支持 draft/published 状态、软删除、审计日志。

#### 3.4.1 数据库变更

```sql
-- 三张主表统一加列
ALTER TABLE table_info
  ADD COLUMN status       VARCHAR(16) NOT NULL DEFAULT 'published',
  ADD COLUMN is_deleted   TINYINT(1)  NOT NULL DEFAULT 0;

ALTER TABLE column_info
  ADD COLUMN status       VARCHAR(16) NOT NULL DEFAULT 'published',
  ADD COLUMN is_deleted   TINYINT(1)  NOT NULL DEFAULT 0;

ALTER TABLE logical_table_relation
  ADD COLUMN status       VARCHAR(16) NOT NULL DEFAULT 'published',
  ADD COLUMN is_deleted   TINYINT(1)  NOT NULL DEFAULT 0;

-- 审计日志表
CREATE TABLE semantic_audit_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type   VARCHAR(32)  NOT NULL,
    entity_id     INT          NOT NULL,
    datasource_id INT          NOT NULL,
    operation     VARCHAR(16)  NOT NULL,
    changed_by    VARCHAR(64)  NOT NULL,
    changed_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_value     JSON,
    new_value     JSON,
    INDEX idx_entity (entity_type, entity_id)
) ENGINE=InnoDB;
```

#### 3.4.2 SemanticCatalog 变更

```java
// 加载语义时只加载 published + is_deleted=0
public CatalogContext createContext(Datasource datasource) {
    return new CatalogContext(
        datasource,
        semanticVisibilitySupport.createVisibilityContext(datasource),
        "published",  // 新增：状态过滤
        false         // 新增：is_deleted 过滤
    );
}
```

---

### Phase 5：Completeness 统计（P2，2-3 天）

**目标**：提供语义覆盖率看板，指导运营人员优先填充。

#### 3.5.1 新建服务

```java
@Service
public class SemanticCompletenessService {
    
    public CompletenessReport calculate(Integer datasourceId) {
        // 计算各维度覆盖率
        double tableDescCoverage = calculateTableDescCoverage(datasourceId);
        double columnDescCoverage = calculateColumnDescCoverage(datasourceId);
        double tableRoleCoverage = calculateTableRoleCoverage(datasourceId);
        double preferredTimeCoverage = calculatePreferredTimeCoverage(datasourceId);
        double relationCoverage = calculateRelationCoverage(datasourceId);
        
        // 加权求和
        double overallScore = tableDescCoverage * 0.25 
            + columnDescCoverage * 0.25
            + tableRoleCoverage * 0.20
            + preferredTimeCoverage * 0.15
            + relationCoverage * 0.10
            + publishedRatio * 0.05;
        
        return new CompletenessReport(overallScore, ...);
    }
}
```

#### 3.5.2 新建接口

```java
@GetMapping("/api/tableinfo/semantic/completeness")
public CompletenessReport getCompleteness(@RequestParam Integer datasourceId) {
    return completenessService.calculate(datasourceId);
}
```

---

## 四、实施优先级总结

| Phase | 内容 | 工作量 | 优先级 | 对 SQL 准确率影响 |
|---|---|---|---|---|
| **Phase 1** | 高级字段语义（semantic_type/time_role/enum） | 3-5 天 | **P0** | **高**（VLDB 论文验证） |
| **Phase 2** | 分析推理增强（table_role/join_hint） | 3-5 天 | **P1** | **高**（事实/维度表识别） |
| **Phase 3** | 指标语义层（MetricDefinition/GetMetricsTool） | 5-7 天 | **P1** | **高**（聚合口径统一） |
| **Phase 4** | 治理能力（status/soft_delete/audit） | 3-5 天 | **P2** | 低（运维需求） |
| **Phase 5** | Completeness 统计 | 2-3 天 | **P2** | 低（运维需求） |

**建议开工顺序**：Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5

---

## 五、与 AgentScope-Java 适配方案的协同

| 语义层 Phase | AgentScope-Java 能力 | 协同点 |
|---|---|---|
| Phase 1-3 | **ToolGroup** | 每个数据源的语义上下文绑定到对应 ToolGroup |
| Phase 1-3 | **AG-UI Protocol** | 工具调用事件自动携带语义信息，前端可结构化展示 |
| Phase 4 | **Chunk Callback** | 审计日志可通过 Callback 统一记录，替代 AOP |
| Phase 5 | - | 独立实现，无框架依赖 |

**建议**：先完成 AgentScope-Java 适配方案的 Phase 1（ToolGroup + AG-UI），再开工语义层 Phase 1-3，可复用框架能力。

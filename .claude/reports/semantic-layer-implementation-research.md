# 语义层演进方向调研报告

生成时间：2026-05-17  
范围：DataAgent 语义层下一步实现方向的具体技术调研  
调研对象：高级字段语义、指标语义层、分析推理增强语义、治理能力、completeness 统计  
调研来源：dbt MetricFlow、Cube.js、Wren AI、DataHub、OpenMetadata、Atlan、相关 NL2SQL 学术论文（VLDB 2025）

> 前置依赖：本报告基于已完成的三层语义基线（表/列/关系）和当前代码事实（见 `../reference/semantic-layer-baseline.md`）。  
> 不重复既有缺口分析（见 `semantic-layer-full-report.md`），只聚焦**具体实现方案**。

---

## 一、高级字段语义实现方案

### 1.1 调研背景

当前 `ColumnSemanticInfo` 只有 `column_description` + `is_visible`，`ColumnSemanticPrompt` 输出六个字段（name/type/primaryKey/nullable/defaultValue/description）。LLM 无法区分维度列和指标列、无法识别时间轴、无法理解枚举值含义。

**VLDB 2025 NL2SQL 长上下文论文的实验结论**：column sample values（example_values）对准确率的提升排名第二，仅次于 hints，高于 few-shot examples。

### 1.2 semantic_type 枚举设计

参照 dbt MetricFlow（measure/dimension/entity/time）和 Cube.js（sum/count/string/time/boolean）的语义分类：

```
DIMENSION    — 维度：用于 GROUP BY / WHERE 过滤的分类属性（城市、类别、渠道）
MEASURE      — 指标：可以被聚合的数值（金额、数量、次数）
TIME         — 时间：时间戳/日期类，具有 time_role
IDENTIFIER   — 标识：主键或业务 ID，通常不聚合也不过滤
ENUM         — 枚举：有限值域的状态/类型字段，配合 enum_schema 使用
FOREIGN_KEY  — 外键：关联外部表的 ID 列，辅助 JOIN 决策
STATUS       — 状态：布尔或离散的状态字段（is_active / order_status）
```

**对 SQL 生成的直接影响**：
- LLM 看到 `MEASURE` → 自动考虑 SUM/AVG/COUNT，而非把它当 WHERE 条件
- LLM 看到 `ENUM` + `enumValues` → 在 `WHERE status = ?` 时直接使用 `PAID` 而非猜测中文值
- LLM 看到 `TIME` + `timeRole=EVENT_TIME` → 优先把该列用于时间范围过滤

### 1.3 time_role 枚举设计

```
EVENT_TIME      — 业务事件发生时间（订单创建、交易发生）—— 最常用的时间过滤轴
PROCESS_TIME    — 数据入库/处理时间（etl_time、update_time）—— 一般不做业务过滤
REPORT_DATE     — 报表汇总日期（预聚合时间维度）
FISCAL_PERIOD   — 财务期间（fiscal_year、fiscal_quarter）
EXPIRY_TIME     — 截止/过期时间（expire_at、due_date）
PARTITION_KEY   — 分区键（仅用于分区裁剪，加速查询）
```

**Prompt 片段示例**：
```
[order_date] DATETIME  [semanticType=TIME, timeRole=EVENT_TIME]
  用途：订单业务发生日期，用于"最近N天""按月统计"等时间过滤和分组

[etl_updated_at] DATETIME  [semanticType=TIME, timeRole=PROCESS_TIME]
  用途：数据更新时间戳，请勿用于业务时间过滤
```

### 1.4 enum_schema 存储方案

**结论：默认存 JSON 字符串在 `column_info` 表，枚举值 > 50 或需独立维护时才拆独立表。**

| 情况 | 方案 |
|---|---|
| 枚举值 ≤ 20，较稳定 | `enum_schema` JSON 内联于 `column_info` 行 |
| 枚举值 20-50 | 仍存 JSON，description 中补充说明 |
| 枚举值 > 50 | 独立 `column_enum_values` 表，`enum_schema` 存 `{"type":"ref","refTable":"column_enum_values"}` |
| 枚举来自外部码表 | 存 `{"type":"reference","refTable":"dict_xxx"}`，运行时动态查 |

**JSON 格式设计**（存入 `column_info.enum_schema`）：
```json
{
  "type": "code_label",
  "values": [
    {"code": "PENDING",   "label": "待支付"},
    {"code": "PAID",      "label": "已支付"},
    {"code": "CANCELLED", "label": "已取消"}
  ]
}
```

### 1.5 example_values 最佳使用方式

按 `semantic_type` 差异化渲染，不直接把原始 JSON 扔给 LLM：

| semantic_type | 渲染策略 |
|---|---|
| TIME | 只给格式示例，不给具体值：`格式示例 "2024-03-15 14:30:00"` |
| ENUM / STATUS | 给完整枚举 + 中文：`取值范围 [PENDING=待支付, PAID=已支付]` |
| MEASURE | 给量级提示：`数值，单位元，示例范围 0.01~99999.99` |
| DIMENSION | 给 3-5 个高频样本值：`常见值 "北京", "上海", "广州"` |
| IDENTIFIER | 不渲染（ID 对 SQL 生成无帮助） |

### 1.6 数据库变更方案

在 `column_info` 表**直接 ALTER ADD COLUMN**，不新建表（数量级低，无性能顾虑；现有 MyBatis XML 用 `<set>` 动态条件，改动最小）：

```sql
ALTER TABLE column_info
  ADD COLUMN display_name   VARCHAR(128)  NULL  COMMENT '业务展示名（中文）',
  ADD COLUMN semantic_type  VARCHAR(32)   NULL  COMMENT 'DIMENSION/MEASURE/TIME/IDENTIFIER/ENUM/FOREIGN_KEY/STATUS',
  ADD COLUMN time_role      VARCHAR(32)   NULL  COMMENT 'EVENT_TIME/PROCESS_TIME/REPORT_DATE/FISCAL_PERIOD/EXPIRY_TIME/PARTITION_KEY',
  ADD COLUMN enum_schema    VARCHAR(2000) NULL  COMMENT '枚举定义 JSON: {type,values:[{code,label}]}',
  ADD COLUMN example_values VARCHAR(500)  NULL  COMMENT '示例值 JSON 数组，最多 5 个';
```

**Java 实体变更**：`ColumnSemanticInfo.java` 加同名五个 `String` 字段（DB 存 VARCHAR，Java 层用枚举常量类处理）。

**ColumnSemanticPrompt record 扩展**：
```java
public record ColumnSemanticPrompt(
    String name, String type, Boolean primaryKey,
    Boolean nullable, String defaultValue, String description,
    // 新增字段（null 值用 @JsonInclude(NON_NULL) 不序列化，避免污染 LLM 上下文）
    String displayName,
    String semanticType,
    String timeRole,
    List<EnumValueEntry> enumValues,   // 从 enum_schema 解析后的结构化列表
    List<String> exampleValues
) {}
```

**GetTableSchemaTool 返回 JSON 示例片段**（LLM 实际看到的效果）：
```json
{
  "name": "status", "type": "VARCHAR", "semanticType": "ENUM", "displayName": "订单状态",
  "enumValues": [
    {"code": "PENDING", "label": "待支付"},
    {"code": "PAID",    "label": "已支付"}
  ]
},
{
  "name": "created_at", "type": "DATETIME",
  "semanticType": "TIME", "timeRole": "EVENT_TIME",
  "displayName": "下单时间", "exampleValues": ["2024-01-15 09:30:00"]
},
{
  "name": "amount", "type": "DECIMAL",
  "semanticType": "MEASURE", "displayName": "订单金额",
  "description": "实付金额（元），不含运费", "exampleValues": ["99.00", "299.50"]
}
```

---

## 二、指标语义层实现方案

### 2.1 为何指标必须独立建模

**dbt MetricFlow 的核心启示**：metric 不等于列，metric 是独立语义对象。表/列/关系解决**结构理解**，metric 解决**口径理解**。如果把指标塞进 `ColumnSemanticInfo`，字段语义和业务口径会混乱。

### 2.2 核心实体设计：MetricDefinition

综合 dbt MetricFlow 和 Cube.js，提炼最小可用实体：

```java
// 新文件：entity/MetricDefinition.java
public class MetricDefinition {
    private Integer id;
    private Integer datasourceId;
    private String metricName;       // 唯一名称（英文，供 LLM 工具引用）
    private String displayName;      // 展示名（中文）
    private String description;      // 业务说明
    private String metricType;       // SIMPLE/RATIO/DERIVED/CUMULATIVE
    private String sourceTable;      // 主表名
    private String expression;       // SQL 聚合表达式
    private String aggregation;      // SUM/COUNT/COUNT_DISTINCT/AVG/MAX/MIN/RATIO/CUSTOM
    private String filterCondition;  // WHERE 附加条件（纯 SQL 片段，无 WHERE 关键字）
    private String dimensionColumns; // JSON 数组：可 GROUP BY 的维度列名
    private String timeColumn;       // 关联时间轴列名
    private Boolean isEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

```sql
CREATE TABLE metric_definition (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    datasource_id    INT          NOT NULL,
    metric_name      VARCHAR(128) NOT NULL,
    display_name     VARCHAR(128) NOT NULL,
    description      TEXT,
    metric_type      VARCHAR(32)  NOT NULL  COMMENT 'SIMPLE|RATIO|DERIVED|CUMULATIVE',
    source_table     VARCHAR(128) NOT NULL,
    expression       VARCHAR(500) NOT NULL  COMMENT 'SQL聚合表达式片段，不含别名',
    aggregation      VARCHAR(32)  NOT NULL  COMMENT 'SUM|COUNT|COUNT_DISTINCT|AVG|MAX|MIN|RATIO|CUSTOM',
    filter_condition VARCHAR(500)            COMMENT '附加WHERE条件，无需WHERE关键字',
    dimension_columns VARCHAR(500)           COMMENT 'JSON数组，可group by的列名',
    time_column      VARCHAR(128)            COMMENT '关联时间维度列名',
    is_enabled       TINYINT(1)   NOT NULL  DEFAULT 1,
    create_time      DATETIME     NOT NULL,
    update_time      DATETIME     NOT NULL,
    UNIQUE KEY uk_metric_name (datasource_id, metric_name),
    INDEX idx_datasource (datasource_id),
    INDEX idx_source_table (datasource_id, source_table)
) ENGINE=InnoDB COMMENT='业务指标定义表';
```

### 2.3 expression 字段设计：支持多种聚合类型

| metric_type | aggregation | expression 示例 | 生成 SQL 片段 |
|---|---|---|---|
| SIMPLE | SUM | `amount` | `SUM(amount)` |
| SIMPLE | COUNT | `id` | `COUNT(id)` |
| SIMPLE | COUNT_DISTINCT | `user_id` | `COUNT(DISTINCT user_id)` |
| SIMPLE | CUSTOM | `SUM(amount) / COUNT(DISTINCT user_id)` | 直接使用 expression |
| RATIO | RATIO | `{"numerator":"SUM(paid_amount)","denominator":"SUM(total_amount)"}` | `SUM(paid_amount) / NULLIF(SUM(total_amount), 0)` |

**MetricSqlBuilder 核心逻辑**（新建 `service/metric/MetricSqlBuilder.java`）：
```java
public String buildSelectClause(MetricDefinition metric, String alias) {
    return switch (metric.getAggregation()) {
        case "SUM"            -> "SUM(" + metric.getExpression() + ") AS " + alias;
        case "COUNT"          -> "COUNT(" + metric.getExpression() + ") AS " + alias;
        case "COUNT_DISTINCT" -> "COUNT(DISTINCT " + metric.getExpression() + ") AS " + alias;
        case "AVG"            -> "AVG(" + metric.getExpression() + ") AS " + alias;
        case "RATIO"          -> buildRatioClause(metric.getExpression(), alias);
        case "CUSTOM"         -> "(" + metric.getExpression() + ") AS " + alias;
        default               -> metric.getExpression() + " AS " + alias;
    };
}
```

### 2.4 GetMetricsTool：Agent 工具设计

**新建 `agent/tools/GetMetricsTool.java`**：

```java
@Tool(
    name = "get_metrics",
    description = "获取当前数据源已定义的业务指标列表。返回指标名称、展示名、说明、" +
                  "聚合方式、所在表、可关联维度和时间轴。" +
                  "生成包含指标计算的 SQL 时应优先调用此工具。"
)
public ToolResult<List<MetricPromptItem>> getMetrics(
    @ToolParam(name = "table_name", description = "可选，按主表名过滤") String tableName
) { ... }
```

**MetricPromptItem record**（LLM 消费格式，预渲染好的 SQL 片段直接可用）：
```java
public record MetricPromptItem(
    String metricName,       // "total_revenue"
    String displayName,      // "总收入"
    String description,      // "订单实付金额总和，不含取消订单"
    String sourceTable,      // "orders"
    String aggregation,      // "SUM"
    String sqlExpression,    // "SUM(amount)" —— 预渲染好，LLM 直接粘贴到 SELECT
    String filterCondition,  // "status != 'CANCELLED'"
    List<String> dimensions, // ["city", "channel", "created_at"]
    String timeColumn        // "created_at"
) {}
```

**get_metrics 返回 JSON 示例**：
```json
{
  "success": true,
  "data": [
    {
      "metricName": "total_revenue", "displayName": "总收入",
      "description": "订单实付金额总和，不含取消订单",
      "sourceTable": "orders", "aggregation": "SUM",
      "sqlExpression": "SUM(amount)",
      "filterCondition": "status != 'CANCELLED'",
      "dimensions": ["city", "channel", "created_at"],
      "timeColumn": "created_at"
    },
    {
      "metricName": "conversion_rate", "displayName": "支付转化率",
      "description": "支付订单数 / 总订单数",
      "sourceTable": "orders", "aggregation": "RATIO",
      "sqlExpression": "COUNT(CASE WHEN status='PAID' THEN 1 END) / NULLIF(COUNT(id), 0)",
      "filterCondition": null,
      "dimensions": ["channel"], "timeColumn": "created_at"
    }
  ]
}
```

**此格式让 LLM 能正确使用的原因**：
1. `sqlExpression` 是预渲染好的完整 SQL 聚合片段，LLM 直接放入 SELECT，无需推理聚合逻辑
2. `filterCondition` 明确必须加的 WHERE 条件，避免 LLM 遗漏业务过滤
3. `dimensions` 明确哪些列可以 GROUP BY，防止 LLM 随意分组
4. `timeColumn` 与 `column_info.time_role=EVENT_TIME` 形成呼应

### 2.5 指标层与现有语义层的边界

```
表语义   → 回答：这张表是什么业务对象
列语义   → 回答：这个字段是什么意思，是维度候选还是度量候选
关系语义 → 回答：表与表怎么 JOIN
指标语义 → 回答：这个业务指标怎么算、按什么聚合、用哪些过滤、依赖哪些表与字段
```

---

## 三、分析推理增强语义实现方案

### 3.1 table_role：事实表 vs 维度表分类

**参考来源**：Knowledge Graph-powered NL2SQL、CIDR 2024 论文、TailorSQL（VLDB 2025）

| table_role 值 | 含义 | AI SQL 影响 |
|---|---|---|
| `FACT` | 事实表：含度量值，行数巨大，外键多 | 优先从此表找度量列；SELECT COUNT/SUM 时必用此表 |
| `DIMENSION` | 维度表：含描述性属性，行数少，被 FACT 引用 | GROUP BY 优先选此表属性；不直接 COUNT DISTINCT |
| `BRIDGE` | 多对多关系的桥接表 | 通常只用于 JOIN，不做聚合 |
| `AGGREGATE` | 预聚合宽表 | 比 FACT 优先级高，减少全表扫描 |
| `LOG` | 日志流水表：时序数据，分区查询 | 必须带时间过滤，禁止全表扫 |
| `CONFIG` | 配置字典表：枚举值/参数 | 通常不参与聚合，只做 JOIN 扩充字段名称 |

**扩展 `table_info` 表**：
```sql
ALTER TABLE table_info
  ADD COLUMN table_role              VARCHAR(32)  NULL  COMMENT 'FACT|DIMENSION|BRIDGE|AGGREGATE|LOG|CONFIG',
  ADD COLUMN preferred_time_column   VARCHAR(128) NULL  COMMENT '首选时间过滤列，如 created_at',
  ADD COLUMN partition_column        VARCHAR(128) NULL  COMMENT '分区键列名，查询必须包含此条件',
  ADD COLUMN estimated_row_count     VARCHAR(16)  NULL  COMMENT '<1K|<1M|<100M|>100M，提示 LLM 是否全扫';
```

### 3.2 preferred_time_column / partition_column Prompt 设计

**问题背景**：一张表可能有多个日期列（`created_at`, `updated_at`, `order_date`, `ds`），LLM 在没有提示时常常选错。

**GetTableSchemaTool 返回的表元信息中增加**：
```json
{
  "name": "fact_orders",
  "tableRole": "FACT",
  "estimatedRowCount": ">100M",
  "preferredTimeColumn": "order_date",
  "partitionColumn": "ds",
  "description": "订单事实表，记录每笔交易明细"
}
```

**对应在 Agent System Prompt 中增加全局规则**：
```
## SQL 生成规则（分析推理）
1. 如果表标记了 partitionColumn，生成的 SQL 必须在 WHERE 中包含该列过滤，否则会触发全分区扫描。
2. 当用户提到"最近N天""本月"等时间词时，优先使用表的 preferredTimeColumn 列。
3. 对于 estimatedRowCount > 100M 的表，禁止生成不带任何过滤条件的 SELECT *。
4. DIMENSION 表的属性列优先用于 GROUP BY；FACT 表的 FK 列不应直接 GROUP BY。
```

### 3.3 join_hint / preferred_relation 设计

**问题背景**（来源：TailorSQL VLDB 2025）：`fact_orders` 可以通过 `customer_id` JOIN `dim_customer`，也可以通过 `delivery_address_id` JOIN `dim_address`，LLM 会随机选择导致语义错误。

**扩展 `logical_table_relation` 表**：
```sql
ALTER TABLE logical_table_relation
  ADD COLUMN is_preferred  TINYINT(1)   NOT NULL DEFAULT 0  COMMENT '同一对表多条关系中的首选路径',
  ADD COLUMN priority      TINYINT      NOT NULL DEFAULT 0  COMMENT '0=普通, 1=首选, 2=强制',
  ADD COLUMN join_hint     VARCHAR(512) NULL               COMMENT '给 LLM 的自然语言使用场景说明',
  ADD COLUMN usage_context VARCHAR(255) NULL               COMMENT '适用场景描述';
```

**join_hint 内容示例**：
```
fact_orders.customer_id → dim_customer.id:
  join_hint = "当查询涉及客户姓名、联系方式、客户等级时使用此 JOIN 路径"

fact_orders.delivery_address_id → dim_address.id:
  join_hint = "当查询涉及收货地址、配送区域时使用此 JOIN 路径"
```

**GetTableSchemaTool 返回的 relations 增加 hint 字段**：
```json
{
  "relationType": "foreign_key",
  "source": "logical",
  "sourceTableName": "fact_orders",
  "sourceColumnNames": ["customer_id"],
  "targetTableName": "dim_customer",
  "targetColumnNames": ["id"],
  "isPreferred": true,
  "joinHint": "查询客户相关信息（姓名/等级/注册时间）时使用"
}
```

### 3.4 recommended_filter_columns：多租户/分区过滤自动注入

**扩展 `table_info` 表**：
```sql
ALTER TABLE table_info
  ADD COLUMN recommended_filters JSON NULL
  COMMENT '推荐/强制过滤条件 JSON 数组';
```

**JSON 格式设计**：
```json
[
  {
    "column": "is_deleted",
    "filterType": "SOFT_DELETE",
    "injectFrom": "constant",
    "value": "0",
    "hint": "软删除过滤，始终追加 AND is_deleted = 0"
  },
  {
    "column": "ds",
    "filterType": "PARTITION",
    "injectFrom": "user_input",
    "hint": "分区字段，缺少时会全分区扫描，请从用户问题中提取日期范围"
  },
  {
    "column": "tenant_id",
    "filterType": "MANDATORY",
    "injectFrom": "session_context",
    "hint": "多租户隔离字段，由系统自动注入，你生成的 SQL 不需要包含此条件"
  }
]
```

`filterType` 三种类型：
- `SOFT_DELETE`：常量，始终固定值注入
- `PARTITION`：用户必须提供，LLM 从 user question 中提取
- `MANDATORY`：来自会话上下文（JWT/session），后端代码注入，LLM 不感知不生成

---

## 四、语义层治理能力实现方案

### 4.1 审计日志表设计

**参考来源**：DataHub MCL（MetadataChangeLog）机制、OpenMetadata entity lifecycle events

**采用"主表保存当前状态 + 独立 audit_log 追加写"混合方案**（比 DataHub 的 Kafka MCL 更轻量，适合小项目）：

```sql
CREATE TABLE semantic_audit_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type   VARCHAR(32)  NOT NULL  COMMENT 'table_info|column_semantic|table_relation|metric_definition',
    entity_id     INT          NOT NULL,
    datasource_id INT          NOT NULL,
    operation     VARCHAR(16)  NOT NULL  COMMENT 'CREATE|UPDATE|DELETE|PUBLISH|REVERT|ARCHIVE',
    changed_by    VARCHAR(64)  NOT NULL,
    changed_at    DATETIME     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
    old_value     JSON                   COMMENT '变更前快照（关键字段）',
    new_value     JSON                   COMMENT '变更后快照',
    change_reason VARCHAR(255),
    request_id    VARCHAR(64),
    INDEX idx_entity       (entity_type, entity_id),
    INDEX idx_ds_time      (datasource_id, changed_at DESC),
    INDEX idx_changed_by   (changed_by)
) ENGINE=InnoDB COMMENT='语义层变更审计日志，append-only，禁止 UPDATE/DELETE';
```

**Spring AOP 实现思路**（零侵入业务代码）：
```java
@Aspect @Component
public class SemanticAuditAspect {
    // 拦截 semantic 包下 Service 的 save/update/delete 方法
    // 自动抓取 before/after 快照写入 semantic_audit_log
}
```

### 4.2 draft / published 机制

**在三张语义主表统一增加状态字段**：
```sql
ALTER TABLE table_info
  ADD COLUMN status         VARCHAR(16) NOT NULL DEFAULT 'draft'
                            COMMENT 'draft|published|deprecated|archived',
  ADD COLUMN published_at   DATETIME,
  ADD COLUMN published_by   VARCHAR(64);

-- column_info 和 logical_table_relation 同理
```

**状态流转规则**：
```
draft → published   （人工确认后，AI SQL 生成才加载此语义）
published → deprecated  （标记废弃，查询仍可见，completeness 中降权）
deprecated → archived   （彻底隐藏，不参与 completeness 和 AI 消费）
draft → archived    （放弃草稿）
```

**业务规则**：
- `SemanticCatalog` 在加载语义时，默认只加载 `status = 'published'` 的语义（加 `is_deleted = 0`）
- completeness 看板中，`draft` 条目只计入"草稿覆盖率"，不计入"有效覆盖率"
- 现有管理接口默认查询不过滤 status（用于管理界面），新增 `?status=published` 参数支持过滤

### 4.3 软删除方案

**在三张语义主表统一增加**：
```sql
ADD COLUMN is_deleted   TINYINT(1) NOT NULL DEFAULT 0,
ADD COLUMN deleted_time DATETIME,
ADD COLUMN deleted_by   VARCHAR(64);
```

选用 `is_deleted` + `deleted_time` + `deleted_by` 三字段方案，不用归档表（归档表需维护两套 Mapper，JOIN 关系复杂）。配合 MyBatis 全局拦截器透明过滤 `is_deleted = 0`。

---

## 五、Completeness 统计实现方案

### 5.1 评分公式设计

**参考来源**：Atlan 加权求和模型、Europeana 学术公式

针对 DataAgent 场景的评分维度（总分 100）：

| 评分维度 | 权重 | 说明 |
|---|---|---|
| 表描述覆盖率 | 25 分 | 有 description 的表数 / 总表数 |
| 列描述覆盖率 | 25 分 | 有 description 的列数 / 所有表总列数 |
| table_role 覆盖率 | 20 分 | AI 推理必需，权重高 |
| preferred_time_column 覆盖率 | 15 分 | 影响时间范围查询准确率 |
| 关系配置覆盖率 | 10 分 | 有逻辑关系的表 / 有 FK 但无逻辑关系的表 |
| published 比例 | 5 分 | published 表数 / 总表数 |

另外单独输出 **ai_readiness_score**（只计算 AI 推理相关字段：table_role、preferred_time_col、join_hints），与 overall_score 分开展示。

### 5.2 Completeness API 设计

```
GET /api/tableinfo/semantic/completeness?datasourceId={id}
```

返回结构：
```json
{
  "datasourceId": 1,
  "datasourceName": "prod_mysql",
  "summary": {
    "overallScore": 0.63,
    "totalTables": 42,
    "publishedTables": 28,
    "draftTables": 10,
    "unregisteredTables": 4
  },
  "dimensionBreakdown": {
    "tableDescriptionCoverage": 0.81,
    "columnDescriptionCoverage": 0.57,
    "tableRoleCoverage": 0.45,
    "preferredTimeColCoverage": 0.38,
    "relationCoverage": 0.72,
    "publishedRatio": 0.67
  },
  "aiReadinessScore": 0.58,
  "aiReadinessTips": [
    "45% 的表缺少 table_role 分类，会影响事实/维度表的智能 JOIN 选择",
    "62% 的表缺少 preferred_time_column，时间范围查询会产生歧义"
  ],
  "tables": [
    {
      "tableName": "fact_orders",
      "score": 0.90,
      "status": "published",
      "missing": [],
      "columnDescCoverage": 0.92
    },
    {
      "tableName": "dim_product",
      "score": 0.40,
      "status": "draft",
      "missing": ["table_role", "preferred_time_column", "column_descriptions(6/15)"],
      "columnDescCoverage": 0.40
    }
  ]
}
```

**实现位置**：`service/semantic/SemanticCompletenessService.java`（新建），实时计算不缓存（表数量通常 < 500，无性能问题）。

### 5.3 前端展示方案（供参考）

**三层结构**：
1. **全局看板**：进度条 + 数字（overall_score + 每个维度分项）+ AI 就绪分单独标注
2. **表级热力图**：按 score 着色（红 0-40 / 黄 40-70 / 绿 70-100），按 score 升序排列，运营人员优先填充最差的表
3. **表详情下钻**：展示该表的待完成项 TODO 清单，直接点击跳转到对应语义编辑页

---

## 六、DDL 变更汇总（增量，对现有表无破坏性）

```sql
-- ① 高级字段语义（column_info 加列）
ALTER TABLE column_info
  ADD COLUMN display_name   VARCHAR(128)  NULL  COMMENT '业务展示名',
  ADD COLUMN semantic_type  VARCHAR(32)   NULL  COMMENT 'DIMENSION|MEASURE|TIME|IDENTIFIER|ENUM|FOREIGN_KEY|STATUS',
  ADD COLUMN time_role      VARCHAR(32)   NULL  COMMENT 'EVENT_TIME|PROCESS_TIME|REPORT_DATE|FISCAL_PERIOD|EXPIRY_TIME|PARTITION_KEY',
  ADD COLUMN enum_schema    VARCHAR(2000) NULL  COMMENT '枚举定义 JSON',
  ADD COLUMN example_values VARCHAR(500)  NULL  COMMENT '示例值 JSON 数组';

-- ② 分析增强语义（table_info 加列）
ALTER TABLE table_info
  ADD COLUMN table_role              VARCHAR(32)  NULL  COMMENT 'FACT|DIMENSION|BRIDGE|AGGREGATE|LOG|CONFIG',
  ADD COLUMN preferred_time_column   VARCHAR(128) NULL  COMMENT '首选时间过滤列',
  ADD COLUMN partition_column        VARCHAR(128) NULL  COMMENT '分区键列名，查询必须包含',
  ADD COLUMN estimated_row_count     VARCHAR(16)  NULL  COMMENT '<1K|<1M|<100M|>100M',
  ADD COLUMN recommended_filters     JSON         NULL  COMMENT '推荐/强制过滤条件 JSON 数组';

-- ③ 关系推理增强（logical_table_relation 加列）
ALTER TABLE logical_table_relation
  ADD COLUMN is_preferred  TINYINT(1)   NOT NULL DEFAULT 0  COMMENT '首选 JOIN 路径',
  ADD COLUMN priority      TINYINT      NOT NULL DEFAULT 0  COMMENT '0普通/1首选/2强制',
  ADD COLUMN join_hint     VARCHAR(512) NULL               COMMENT '给 LLM 的使用场景说明',
  ADD COLUMN usage_context VARCHAR(255) NULL               COMMENT '适用场景描述';

-- ④ 治理能力（三张主表统一加列）
ALTER TABLE table_info
  ADD COLUMN status       VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft|published|deprecated|archived',
  ADD COLUMN published_at DATETIME,
  ADD COLUMN published_by VARCHAR(64),
  ADD COLUMN is_deleted   TINYINT(1)  NOT NULL DEFAULT 0,
  ADD COLUMN deleted_time DATETIME,
  ADD COLUMN deleted_by   VARCHAR(64),
  ADD INDEX idx_status_active (status, is_deleted, datasource_id);

ALTER TABLE column_info
  ADD COLUMN status       VARCHAR(16) NOT NULL DEFAULT 'draft',
  ADD COLUMN is_deleted   TINYINT(1)  NOT NULL DEFAULT 0,
  ADD COLUMN deleted_time DATETIME,
  ADD COLUMN deleted_by   VARCHAR(64),
  ADD INDEX idx_ci_active (is_deleted, datasource_id);

ALTER TABLE logical_table_relation
  ADD COLUMN status       VARCHAR(16) NOT NULL DEFAULT 'draft',
  ADD COLUMN is_deleted   TINYINT(1)  NOT NULL DEFAULT 0,
  ADD COLUMN deleted_time DATETIME,
  ADD COLUMN deleted_by   VARCHAR(64);

-- ⑤ 审计日志表（全新）
CREATE TABLE semantic_audit_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type   VARCHAR(32)  NOT NULL  COMMENT 'table_info|column_semantic|table_relation|metric_definition',
    entity_id     INT          NOT NULL,
    datasource_id INT          NOT NULL,
    operation     VARCHAR(16)  NOT NULL  COMMENT 'CREATE|UPDATE|DELETE|PUBLISH|REVERT|ARCHIVE',
    changed_by    VARCHAR(64)  NOT NULL,
    changed_at    DATETIME     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
    old_value     JSON,
    new_value     JSON,
    change_reason VARCHAR(255),
    request_id    VARCHAR(64),
    INDEX idx_entity  (entity_type, entity_id),
    INDEX idx_ds_time (datasource_id, changed_at DESC)
) ENGINE=InnoDB COMMENT='语义层变更审计日志，append-only';

-- ⑥ 指标定义表（全新）
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
    is_deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    create_time      DATETIME     NOT NULL,
    update_time      DATETIME     NOT NULL,
    UNIQUE KEY uk_metric_name (datasource_id, metric_name),
    INDEX idx_datasource  (datasource_id),
    INDEX idx_source_table (datasource_id, source_table)
) ENGINE=InnoDB COMMENT='业务指标定义表';
```

---

## 七、关键文件变更清单

### 需要修改的现有文件

| 文件 | 变更内容 |
|---|---|
| `entity/ColumnSemanticInfo.java` | 加 5 个新字段：displayName, semanticType, timeRole, enumSchema, exampleValues |
| `dto/toolresponse/ColumnSemanticPrompt.java` | record 扩展，加 displayName/semanticType/timeRole/enumValues/exampleValues |
| `dto/semantic/ColumnSemanticUpdateRequest.java` | 加新字段的更新入参 |
| `dto/semantic/ColumnSemanticResponse.java` | 加新字段的响应出参 |
| `mapper/ColumnSemanticInfoMapper.xml` | resultMap 和 update 语句补充新字段 |
| `entity/TableInfo.java` | 加 tableRole/preferredTimeColumn/partitionColumn/estimatedRowCount/recommendedFilters/status/isDeleted 等字段 |
| `dto/semantic/TableSemanticUpdateRequest.java` | 加新字段 |
| `dto/semantic/TableSemanticResponse.java` | 加新字段 |
| `mapper/TableInfoMapper.xml` | resultMap 和 update 补充 |
| `entity/LogicalTableRelation.java` | 加 isPreferred/priority/joinHint/usageContext/status/isDeleted |
| `mapper/LogicalTableRelationMapper.xml` | resultMap 和 update 补充 |
| `dto/toolresponse/TableRelationToolResponse.java` | 加 isPreferred/joinHint |
| `service/semantic/SemanticCatalog.java` | `listVisibleRelations` 加载时过滤 `is_deleted=0, status=published`（或按配置）|
| `agent/AgentService.java` | System Prompt 中加入分析增强规则（partition、time、table_role 等） |
| `sql/data_source.sql` | 追加所有 ALTER TABLE + CREATE TABLE |

### 需要新建的文件

| 文件 | 说明 |
|---|---|
| `entity/MetricDefinition.java` | 指标定义实体 |
| `mapper/MetricDefinitionMapper.java` | 指标 Mapper 接口 |
| `mapper/MetricDefinitionMapper.xml` | 指标 Mapper XML |
| `service/semantic/MetricDefinitionService.java` | 指标服务接口 |
| `service/semantic/impl/MetricDefinitionServiceImpl.java` | 指标服务实现 |
| `service/metric/MetricSqlBuilder.java` | SQL 聚合表达式构建器 |
| `service/semantic/SemanticCompletenessService.java` | completeness 统计服务 |
| `agent/tools/GetMetricsTool.java` | 指标工具（注册到 Toolkit） |
| `controller/MetricDefinitionController.java` | 指标管理 REST 接口 |
| `controller/SemanticCompletenessController.java` | completeness 查询接口 |
| `dto/semantic/MetricUpsertRequest.java` | 指标创建/更新入参 |
| `dto/semantic/MetricResponse.java` | 指标管理出参 |
| `dto/toolresponse/MetricPromptItem.java` | 指标 Agent 消费格式 |
| `entity/SemanticAuditLog.java` | 审计日志实体 |
| `mapper/SemanticAuditLogMapper.java` / `.xml` | 审计日志 Mapper |
| `service/governance/SemanticAuditService.java` | 审计日志服务 |
| `aop/SemanticAuditAspect.java` | AOP 切面（自动写审计日志） |

---

## 八、分阶段实施优先级建议

### 一期（最高 ROI，1-2 周，纯 ALTER TABLE）

| 工作项 | 改动范围 | 复杂度 |
|---|---|---|
| `column_info` 加 `semantic_type` + `time_role` | 2 列 + DTO + Mapper XML | **简单** |
| `GetTableSchemaTool` 返回新字段（`@JsonInclude(NON_NULL)`） | ColumnSemanticPrompt 扩展 | **简单** |
| `table_info` 加 `table_role` + `preferred_time_column` | 2 列 + DTO + Mapper XML | **简单** |
| Agent System Prompt 加分析推理规则 | AgentService.java 字符串修改 | **简单** |
| 三张主表加 `is_deleted` + `status` 字段 | 6 列 DDL | **简单** |

> **预期效果**：LLM SQL 生成对维度/指标区分、时间列选择、分区过滤的准确率明显提升。

### 二期（功能增量，2-3 周）

| 工作项 | 改动范围 | 复杂度 |
|---|---|---|
| `column_info` 加 `enum_schema` + `example_values` + 差异化渲染 | 2 列 + 序列化逻辑 | **中等** |
| `logical_table_relation` 加 `is_preferred` + `join_hint` | 2 列 + Prompt 逻辑 | **中等** |
| `recommended_filters` JSON + 后处理注入管道 | 1 JSON 列 + Service 后处理 | **中等** |
| `semantic_audit_log` 表 + Spring AOP 切面 | 1 新表 + 1 切面类 | **中等** |
| completeness 统计 API | 1 Service + 1 Controller | **简单** |

### 三期（指标层，3-5 周，新模块）

| 工作项 | 改动范围 | 复杂度 |
|---|---|---|
| `metric_definition` 表 + 实体 + Mapper | 1 新表 + 完整 CRUD | **中等** |
| `MetricSqlBuilder` | SQL 表达式构建工具类 | **中等** |
| `GetMetricsTool` 注册到 Toolkit | 新 Agent 工具 | **中等** |
| `MetricDefinitionController` 管理接口 | REST CRUD | **中等** |
| AgentService System Prompt 加指标使用规则 | 字符串修改 | **简单** |

### 四期（完整治理，按需）

| 工作项 | 复杂度 |
|---|---|
| draft → published 状态流转 API | **简单** |
| completeness 前端看板（热力图 + TODO 清单） | **中等** |
| 版本历史回溯 API（基于 audit_log 查询） | **简单** |
| `display_name` + `estimated_row_count` 填充工具 | **简单** |

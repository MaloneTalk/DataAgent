# 数据语义层完整现状、缺口、风险与演进规划报告（详版）

生成时间：2026-05-10  
项目路径：`E:/github/DataAgent-mt`  
调研范围：`data-agent-backend` 当前语义层、关系层、Agent 工具链、持久化、配置、CI 与接入状态  
调研方式：基于当前仓库代码静态调研，不包含运行验证

---

# 一、报告结论摘要

基于当前代码，我的总体判断是：

> 这个项目已经搭建出一套**可用于 AI 驱动 SQL 推理的结构化语义层骨架**，且已经覆盖到表、列、关系三层；但它距离“完整数据语义层 / 语义治理系统”还有明显差距，主要缺口集中在：**高级字段语义、指标语义、治理能力、运营效率能力、测试保护**。

换句话说：

- **从“给 AI 生成 SQL 提供结构语义”角度看**：已经达到一个相当不错的起点。
- **从“建设一套长期可维护、可协作、可治理的数据语义层”角度看**：目前还处于第一阶段完成、第二阶段刚起步的状态。

我建议把后续规划分成四阶段推进：

1. **收口现有表/列/关系语义能力**  
   先把当前能力做稳、做全、做易维护。
2. **补齐字段高级语义层**  
   让字段不再只有 description，而是具备业务可理解性。
3. **补齐指标与分析语义层**  
   让 Agent 从“结构推理”走向“业务口径推理”。
4. **补齐治理与审计层**  
   让语义层变成长期协作资产，而不是临时配置。

---

# 二、调研对象与证据来源

本次报告基于以下关键代码和配置文件：

## 1. 语义聚合与可见性
- `data-agent-backend/src/main/java/io/github/malonetalk/service/SemanticSchemaService.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/service/SemanticVisibilitySupport.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/service/LogicalTableRelationSupport.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/service/ActiveDatasourceSupport.java`

## 2. 语义管理接口
- `data-agent-backend/src/main/java/io/github/malonetalk/controller/TableSemanticController.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/controller/TableColumnSemanticController.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/controller/TableRelationSemanticController.java`

## 3. 物理元数据读取
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/datasource/SchemaReader.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/datasource/ColumnInfo.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/datasource/TableRelationInfo.java`

## 4. Agent 工具链
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/AgentService.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/tools/GetTablesTool.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/tools/GetTableSchemaTool.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/tools/ExecuteSqlTool.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/dto/tool/ToolResult.java`

## 5. DTO 与实体
- `data-agent-backend/src/main/java/io/github/malonetalk/dto/semantic/*.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/dto/toolresponse/*.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/entity/*.java`

## 6. 持久化与初始化脚本
- `data-agent-backend/src/main/resources/mapper/*.xml`
- `sql/data_source.sql`
- `data-agent-backend/src/main/resources/application.properties`

## 7. 构建与 CI
- `data-agent-backend/pom.xml`
- `.github/workflows/maven.yml`

---

# 三、当前语义层整体架构现状

从现有代码看，当前语义层已经形成了一套“物理结构 -> 语义增强 -> Agent 消费”的完整主链路。

## 3.1 物理元数据来源层

由 `SchemaReader` 负责从真实数据源读取：
- 物理表
- 物理列
- 主键
- 物理外键

### 当前能力
#### 表读取
- `getTables(Datasource datasource)`

#### 列读取
- `getTableSchema(Datasource datasource, String tableName)`

#### 主键读取
- `getPrimaryKeys(Connection conn, String tableName)`

#### 物理外键读取
- `getImportedRelations(Datasource datasource, String tableName)`
- 内部通过 `DatabaseMetaData.getImportedKeys(...)` 读取
- 按 `FK_NAME` / 顺序聚合复合外键
- 输出为 `TableRelationInfo`

### 评价
这一层已经不是“只看表结构”，而是已经具备关系元数据读取能力。这对完整语义层是非常关键的基础。

---

## 3.2 语义持久化层

当前已有四张核心表：

### 1. `datasource`
存储数据源配置与状态。

### 2. `table_info`
存储表级语义：
- `table_name`
- `table_description`
- `domain`
- `datasource_id`
- `is_active`
- `is_visible`
- `create_time`
- `update_time`

### 3. `column_info`
存储列级语义：
- `table_name`
- `column_name`
- `column_description`
- `is_active`
- `is_visible`
- `datasource_id`
- `create_time`
- `update_time`

### 4. `logical_table_relation`
存储关系级语义：
- `source_table_name`
- `source_column_names_json`
- `source_column_signature`
- `target_table_name`
- `target_column_names_json`
- `target_column_signature`
- `relation_type`
- `description`
- `is_enabled`
- `datasource_id`
- `create_time`
- `update_time`

### 当前持久化评价
这套模型已经覆盖：
- 表语义
- 列语义
- 关系语义

说明当前项目已经完成了“语义层最基础三要素”的落库。

---

## 3.3 语义聚合层

当前语义聚合有两层：

### A. `SemanticVisibilitySupport`
职责偏领域规则与 merge 支撑：
- 物理表 + 表语义 merge
- 物理列 + 列语义 merge
- 名称归一化
- 表/列可见性判断
- 查找 merged table / merged column

### B. `SemanticSchemaService`
职责偏应用层聚合输出：
- 查询表语义列表
- 查询列语义列表
- 更新/重置表、列语义
- 输出可见表 prompt
- 输出单表 schema prompt
- 聚合物理关系 + 逻辑关系后输出 relations

### 架构评价
这一设计是合理的：
- `SemanticVisibilitySupport` 负责底层 merge / visibility
- `SemanticSchemaService` 负责外部业务输出

但同时也暴露出后续一个演进点：
- `SemanticSchemaService` 目前同时承担“管理接口用聚合”和“AI 输出组装”两类职责，未来可以考虑进一步分层。

---

## 3.4 管理接口层

当前语义层已经有 3 套管理接口：

### 1. 表语义
路径：`/api/tableinfo/semantic/tables`

支持：
- 列表查询
- 更新
- 重置

### 2. 列语义
路径：`/api/tableinfo/{tableName}/semantic/columns`

支持：
- 列表查询
- 更新
- 重置

### 3. 关系语义
路径：`/api/tableinfo/semantic/relations`

支持：
- 候选表查询
- 候选字段查询
- 关系列表
- 新增
- 修改
- 启用/禁用
- 删除

### 接口层评价
这说明当前语义层已经从“只给 AI 用”演进成“有后台管理能力”的系统，而不是单纯内嵌逻辑。

---

## 3.5 Agent 消费层

当前 Agent 已明确依赖语义层：

### `AgentService` 的 system prompt
当前强制流程为：
1. `get_tables`
2. `get_table_schema`
3. 生成 SELECT SQL
4. `execute_sql`
5. 回答用户

且明确要求 tool 返回统一的 `success/data/error` 协议。

### 当前 Agent 能看到的结构语义
#### `get_tables`
返回：
- 表名
- domain
- 表描述

#### `get_table_schema`
返回：
- 表名
- domain
- 表描述
- 列列表
  - name
  - type
  - primaryKey
  - nullable
  - defaultValue
  - description
- 关系列表
  - relationType
  - source
  - sourceTableName
  - sourceColumnNames
  - targetTableName
  - targetColumnNames
  - description

### Agent 消费层评价
从 AI SQL 推理角度看，这已经是一套相当扎实的结构化语义输入层了。

---

# 四、当前已经具备的能力矩阵

## 4.1 结构语义能力
| 能力 | 当前状态 | 说明 |
|---|---|---|
| 物理表读取 | 已具备 | `SchemaReader.getTables` |
| 物理列读取 | 已具备 | `SchemaReader.getTableSchema` |
| 主键读取 | 已具备 | `SchemaReader.getPrimaryKeys` |
| 物理外键读取 | 已具备 | `SchemaReader.getImportedRelations` |
| 表语义落库 | 已具备 | `table_info` |
| 列语义落库 | 已具备 | `column_info` |
| 逻辑关系落库 | 已具备 | `logical_table_relation` |

## 4.2 语义增强能力
| 能力 | 当前状态 | 说明 |
|---|---|---|
| 表描述 | 已具备 | 支持更新/重置 |
| 表 domain | 已具备 | 支持更新 |
| 表可见性 | 已具备 | 参与 merge |
| 列描述 | 已具备 | 支持更新/重置 |
| 列可见性 | 已具备 | 参与 merge |
| 关系描述 | 已具备 | 逻辑关系可维护 |
| 逻辑覆盖物理关系 | 已具备 | 通过 relation key 覆盖 |
| 表/列/关系候选可见性校验 | 已具备 | `SemanticVisibilitySupport` |

## 4.3 管理能力
| 能力 | 当前状态 | 说明 |
|---|---|---|
| 表语义列表 | 已具备 | 无分页 |
| 列语义列表 | 已具备 | 无分页 |
| 关系列表 | 已具备 | 无分页 |
| 表语义更新 | 已具备 | 单条 |
| 列语义更新 | 已具备 | 单条 |
| 关系创建/修改/启停/删除 | 已具备 | 单条 |
| 候选表/字段接口 | 已具备 | 支持关系绑定 |
| 批量操作 | 未具备 | 缺口 |
| 搜索/分页/排序 | 未具备 | 缺口 |
| 导入导出 | 未具备 | 缺口 |

## 4.4 Agent 能力
| 能力 | 当前状态 | 说明 |
|---|---|---|
| 结构化表列表输出 | 已具备 | `TableSemanticPrompt` |
| 结构化表详情输出 | 已具备 | `TableSchemaSemanticPrompt` |
| 结构化关系列表输出 | 已具备 | `TableRelationToolResponse` |
| 统一 tool 协议 | 已具备 | `ToolResult` |
| 事件类型化流式输出 | 未具备 | 当前 SSE 还是字符串流 |

---

# 五、当前仍然缺失的六大能力层

尽管骨架已经齐全，但距离“完整语义层”还差六类关键能力。

---

# 六、缺口一：字段高级语义层缺失

当前列语义还主要停留在：
- 描述
- 可见性
- 结构信息

这能解决“SQL 怎么写”，但不能很好解决“字段业务上是什么意思”。

## 需要补的字段高级语义

### 1. display name / alias
例如：
- `gmv` -> 成交金额
- `uv` -> 去重访客数

### 2. semantic type
建议支持：
- dimension
- metric
- identifier
- time
- enum
- status
- foreign_key

### 3. time role
例如：
- create_time
- update_time
- event_time
- partition_time

### 4. enum schema
例如：
- `status: 1=待支付,2=已支付,3=取消`

### 5. example values
让 AI 能看到样例值，能更准确理解字段含义。

## 影响
没有这些能力时：
- AI 很难准确区分维度和指标候选
- AI 难以解释状态值
- AI 只能靠字段名猜时间字段
- 缩写字段理解依然不稳定

## 规划建议
新增列高级语义扩展层，优先补：
- `display_name`
- `semantic_type`
- `time_role`
- `enum_schema_json`
- `example_values_json`

---

# 七、缺口二：缺指标语义层

当前系统知道：
- 表是什么
- 列是什么
- 表怎么连

但还不知道：
- 指标是什么
- 指标口径是什么
- 指标怎么聚合
- 指标依赖哪些字段/表

## 典型缺失问题
用户问：
- GMV
- 活跃用户数
- 首单用户数
- 退款率

当前 Agent 只能靠表列猜测，无法稳定给出业务正确答案。

## 建议独立建模
指标语义不要塞进列语义表，建议新增独立模块：
- metric_name
- display_name
- definition
- aggregation_type
- source_table
- source_field / expression
- filter_rule_description
- grain_description
- dependencies_json
- is_enabled

这将是语义层从“结构语义”走向“业务分析语义”的关键一步。

---

# 八、缺口三：缺分析推理增强语义

关系虽然已经有了，但 AI 还缺少更高级的分析提示。

## 还缺的推理增强语义

### 1. 表角色
例如：
- fact
- dimension
- lookup
- bridge
- snapshot

### 2. 推荐时间字段
每张表通常应有：
- preferred time column
- partition column

### 3. 推荐过滤字段
例如：
- tenant_id
- ds
- is_deleted
- status

### 4. preferred relation / join hint
当有多条 relation 时，AI 还不知道哪条是主业务路径。

## 规划建议
先从表语义扩展：
- `table_role`
- `preferred_time_column`
- `recommended_filter_columns_json`
- `preferred_relation_key`

---

# 九、缺口四：缺治理与审计能力

当前语义层是配置层，不是治理层。

## 当前缺失
- 审计日志
- 版本历史
- 草稿 / 发布态
- 软删除
- 回滚能力

## 当前删除策略问题
目前：
- 表 reset / delete 偏硬删除
- 列 reset / delete 偏硬删除
- 关系 delete 偏硬删除

这样短期简单，但长期不利于治理与追溯。

## 建议新增
### A. 审计日志表
建议：`semantic_change_log`
字段包括：
- object_type
- object_key
- action
- before_json
- after_json
- operator
- operate_time

### B. 版本与发布态
建议在语义对象上补：
- `version`
- `publish_status`（draft/published）

---

# 十、缺口五：缺运营效率能力

当前接口都是单对象维护模式。

## 当前缺失
- 分页
- 搜索
- 排序
- 批量启用/禁用
- 批量更新
- 导入导出
- completeness 统计

## 实际影响
如果表、列、关系数量增大：
- 管理会很低效
- 很难推进语义覆盖率
- 很难做运营治理

## 建议优先补
1. 表 / 列 / 关系分页与 keyword 搜索
2. 批量 visible / enabled 更新
3. completeness 统计（覆盖率看板）
4. JSON 导入导出

---

# 十一、缺口六：测试、验证与可观测性缺失

## 1. 测试目录缺失
当前未发现：
- `data-agent-backend/src/test`

虽然 `pom.xml` 已经引入：
- `spring-boot-starter-test`

但仓库里没有实际测试用例。

## 2. 当前最该补的测试
### A. `SemanticVisibilitySupport`
- mergeTables
- mergeColumns
- 可见性判定

### B. `SemanticSchemaService`
- 表/列 merge
- 关系 merge
- 逻辑覆盖物理
- 不可见对象过滤
- `getTableSchema` 输出结构

### C. `LogicalTableRelationServiceImpl`
- create/update/delete
- enable/disable
- `effective` / `invalidReason`
- 候选表/字段

## 3. Agent 端可观测性不足
当前：
- `chat/stream` 返回 `Flux<String>`
- 通过 `MsgUtils.getTextContent()` 扁平化消息
- 前端无法区分 reasoning / tool result / final answer
- `AgentService` 代码里已有 TODO 提示这一点

### 影响
- 调试难
- 前端渲染能力弱
- 无法做结构化展示和问题定位

## 4. CI 现状
`.github/workflows/maven.yml` 当前会执行：
- `mvn verify --file data-agent-backend/pom.xml`

但由于没有测试，当前 CI 更多只是在做：
- 编译
- spotless check
- 基本 verify

---

# 十二、配置与依赖层现状与风险

## 12.1 当前优点
- `application.properties` 中 MyBatis alias 包名已修正为：
  - `io.github.malonetalk.entity`
- `pom.xml` 已集成：
  - Spring Boot Web
  - Validation
  - MyBatis
  - MySQL 驱动
  - AgentScope
  - Spotless
- CI 已接入 Maven verify

## 12.2 当前风险
### 1. 明文数据库配置
`application.properties` 仍存在：
- root / 123

这是明显的环境配置债务。

### 2. SQL stdout 日志开启
- `mybatis.configuration.log-impl=org.apache.ibatis.logging.stdout.StdOutImpl`

可能造成：
- SQL 泄露
- 参数泄露
- 日志噪音过大

### 3. 缺迁移框架
当前没有看到：
- Flyway
- Liquibase
- schema versioning

这意味着当前 `sql/data_source.sql` 更像初始化脚本，而不是完整迁移系统。

---

# 十三、成熟度判断

如果把完整语义层分成五层：

1. 结构层
2. 基础语义层
3. 高级字段语义层
4. 分析语义层
5. 治理层

那么当前成熟度大致如下：

## 1. 结构层：85%+
- 表、列、主键、物理外键读取已具备

## 2. 基础语义层：75%+
- 表、列、关系语义均已具备
- 可见性和 merge 规则也已成型

## 3. 高级字段语义层：15%~25%
- 目前几乎只有 description，没有更高级标签

## 4. 分析语义层：10%~20%
- 缺 metric / role / join hint / 过滤建议

## 5. 治理层：10% 以下
- 缺审计、版本、发布态、软删除

### 总体评价
- 面向 AI SQL 生成：**已经是可用语义层骨架**
- 面向完整数据语义治理：**仍有明显缺口**

---

# 十四、分阶段演进规划（建议）

## Phase 1：收口现有骨架
### 目标
把当前表/列/关系语义做稳、做全、做易维护。

### 任务
1. 表/列/关系列表加分页、搜索、排序
2. 补批量 visible / enabled 操作
3. 补语义 merge 与关系覆盖测试
4. 补 migration / 环境升级说明
5. 改善 Agent 流式输出结构化能力

### 交付结果
- 当前骨架可维护、可回归、可演进

---

## Phase 2：补字段高级语义
### 目标
让字段从“结构说明”升级到“业务可理解语义”。

### 优先字段
- display_name
- semantic_type
- time_role
- enum_schema_json
- example_values_json

### 交付结果
- AI 对字段的理解显著更稳

---

## Phase 3：补指标与分析语义
### 目标
让 Agent 支持业务分析口径推理。

### 任务
1. metric 语义模型
2. table role
3. preferred time column
4. recommended filter columns
5. preferred relation / join hint

### 交付结果
- 从“结构 SQL 生成”升级到“分析 SQL 推理”

---

## Phase 4：补治理与协作层
### 目标
把语义层升级为长期治理系统。

### 任务
1. 审计日志
2. 版本历史
3. draft / published
4. 软删除 / 回滚
5. completeness 看板
6. 导入导出

### 交付结果
- 支持多人协作、可追溯、可回滚

---

# 十五、当前最值得优先做的三件事

如果必须按优先级压缩，我建议：

## P0
1. **补测试**
2. **补分页/搜索/批量操作**
3. **补 migration / 升级策略**

## P1
4. **补字段高级语义**
5. **补 Agent 结构化事件输出**

## P2
6. **补指标语义层**
7. **补治理与审计层**

---

# 十六、最终结论

基于当前代码，我给你的最终判断是：

> 你的语义层第一骨架已经搭成，而且完成度比普通项目高，已经覆盖表、列、关系，并真实接入了 Agent 推理主链路。

因此，后续路线不应该再停留在“继续补几个字段”这种零散思路，而应该明确转向：

> **从结构语义层，升级为业务语义层，再升级为治理层。**

这也是你现在这套代码最自然、最正确的演进路径。

---

# 十七、建议下一步

我建议你下一步直接二选一：

1. **让我把这份详版报告拆成一个“分阶段任务清单”**  
   适合你准备排期和拆任务。

2. **让我直接写一份 Phase 1 实施方案**  
   只聚焦当前最该先做的稳定性与维护性补全。
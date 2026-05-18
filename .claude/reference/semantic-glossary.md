# 语义层术语与边界定义

生成时间：2026-05-10  
用途：统一当前项目中“语义层”相关术语，避免后续文档与实现出现概念漂移。

---

# 一、总原则

本文件只定义“术语在本项目中的含义”，不承担规划、评审或路线图职责。

---

# 二、核心术语

## 1. 物理元数据
指从真实数据源通过 JDBC `DatabaseMetaData` 读取出来的结构信息，包括：
- 表
- 列
- 主键
- 外键

在当前项目中，物理元数据主要由 `SchemaReader` 提供。

---

## 2. 表语义
指附着在物理表之上的语义增强信息。

当前项目中的表语义包括：
- `domain`
- `tableDescription`
- `isVisible`
- `isActive`

表语义当前由 `TableInfo` / `table_info` 持久化。

---

## 3. 列语义
指附着在物理列之上的语义增强信息。

当前项目中的列语义包括：
- `columnDescription`
- `isVisible`
- `isActive`

列语义当前由 `ColumnSemanticInfo` / `column_info` 持久化。

---

## 4. 关系语义
指表与表之间的关系语义增强信息。

当前项目中的关系语义包括：
- 源表
- 源字段组
- 目标表
- 目标字段组
- `relationType`
- `description`
- `isEnabled`

关系语义当前由 `LogicalTableRelation` / `logical_table_relation` 持久化。

---

## 5. 逻辑关系
指不是从数据库物理外键直接读取，而是由系统维护者显式配置的关系。

当前项目中：
- 逻辑关系来源于 `logical_table_relation`
- 可通过管理接口创建、修改、启停、删除

---

## 6. 物理关系
指通过 `SchemaReader.getImportedRelations(...)` 从数据库元数据读取到的外键关系。

当前项目中物理关系输出模型为：
- `TableRelationInfo`

---

## 7. 关系 merge
指把：
- 物理关系
- 逻辑关系

合并成最终对 AI 可见的 `relations` 集合。

当前项目中：
- 逻辑关系和物理关系会统一映射为 `TableRelationToolResponse`
- 同 key 的关系会去重
- 逻辑关系会覆盖物理关系

---

## 8. 可见性（Visibility）
指某对象是否允许进入语义聚合结果并被 AI 消费。

当前项目中有两层可见性：

### 表可见性
由：
- `isVisible`
- `isActive`

共同决定。

### 列可见性
由：
- `isVisible`
- `isActive`

共同决定。

### 关系可见性
关系本身当前使用：
- `isEnabled`（逻辑关系）
- 端点表/列可见性联动判断

如果关系任一端点不可见，则该关系不会进入最终 AI 输出。

---

## 9. effective
用于描述一条逻辑关系在当前语义视图下是否“有效”。

当前项目中：
- `effective=true` 表示该逻辑关系在当前可见表/可见字段条件下可成立
- `effective=false` 表示该关系记录存在，但由于表/列不可见、对象不存在等原因，不会进入最终 AI 视图

它是**关系管理态**的概念，不是物理关系概念。

---

## 10. invalidReason
用于解释为什么一条逻辑关系当前 `effective=false`。

它属于：
- 关系管理响应层语义

不是 AI 输出层语义。

---

## 11. 结构语义层
指当前项目已经基本落地的语义层范围，核心目标是：

> 让 AI 能正确理解数据库结构并更稳地生成 SQL。

当前已覆盖：
- 表语义
- 列语义
- 关系语义
- 物理结构 merge
- Agent 结构化输出

---

## 12. 高级字段语义层
指超出“description + visible”的字段增强层，主要用于提升业务理解能力。

规划中的典型字段包括：
- display name
- semantic type
- time role
- enum schema
- example values

当前代码中尚未落地。

---

## 13. 指标语义层
指对指标、聚合口径、业务分析对象的独立语义建模层。

典型内容包括：
- metric definition
- aggregation type
- filter rule
- dependencies

当前代码中尚未落地。

---

## 14. 分析推理增强语义
指帮助 Agent 更稳定做分析推理的附加语义，不只是结构说明。

典型内容包括：
- table role
- preferred time column
- recommended filter columns
- preferred relation / join hint

当前代码中尚未系统化落地。

---

## 15. 治理层
指支持长期维护、回溯、协作与发布控制的语义管理能力。

典型内容包括：
- 审计日志
- 版本历史
- draft / published 状态
- 软删除 / 回滚
- 完整度统计

当前代码中尚未落地。

---

# 三、边界定义

## 1. 管理 DTO vs AI DTO
### 管理 DTO
位于：
- `dto.semantic`

用途：
- 管理接口输入输出
- 包含 datasource、更新时间、有效性解释等运营字段

### AI DTO
位于：
- `dto.toolresponse`

用途：
- 给 Agent / tool 使用的结构化语义输出
- 强调 name / description / type / relations 等模型消费字段

---

## 2. 摘要 vs 详情
### 表摘要
由 `get_tables` 返回，适用于：
- 先选表
- 快速理解表用途

### 表详情
由 `get_table_schema` 返回，适用于：
- 理解列
- 理解关系
- 生成 SQL

---

## 3. reset vs delete
### reset
当前主要指：
- 删除对应表 / 列的语义记录，使其回退到物理信息主导状态

### delete
当前主要指：
- 删除逻辑关系记录

二者在当前代码里都偏“硬删除”语义，不带版本回滚。

---

## 4. isVisible vs isActive
### isVisible
控制对象是否对上层语义视图可见。

### isActive
控制对象是否处于启用状态。

当前项目里：
- 表 / 列通常要求 `isVisible=true` 且 `isActive=true` 才进入聚合结果。

---

## 5. isEnabled
当前专用于逻辑关系。

含义：
- 关系记录是否参与当前关系聚合
- 如果 `isEnabled=false`，则该关系不会进入 AI 可见 `relations`

---

# 四、当前术语关系图（文字版）

- **物理元数据** 是基础事实源
- **表语义 / 列语义 / 关系语义** 是语义增强层
- **SemanticVisibilitySupport** 负责物理结构与语义增强的 merge 与可见性判断
- **SemanticSchemaService** 负责生成最终对外语义视图
- **管理 DTO** 面向后台维护
- **AI DTO** 面向 Agent/tool 消费
- **结构语义层** 是当前已落地的主体
- **高级字段语义层 / 指标语义层 / 治理层** 是当前后续演进方向

---

# 五、使用建议

如果后续新增文档或代码，请优先遵守以下规则：

1. 不要混用“管理 DTO”和“AI DTO”术语。
2. 不要把“逻辑关系”和“物理关系”混称为同一个来源。
3. 不要把“结构语义层”和“指标语义层”写成同一层。
4. 新增文档若使用上述术语，应以本文件定义为准。
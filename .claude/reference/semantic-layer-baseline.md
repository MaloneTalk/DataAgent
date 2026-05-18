# 语义层事实基线

生成时间：2026-05-10  
**最后更新：2026-05-17**  
范围：基于当前代码的事实快照，不包含评估与建议  
用途：作为当前语义层的"纯事实基线"查阅文档

---

# 一、当前语义层覆盖范围

当前语义层已覆盖三类对象：

1. **表语义**
2. **列语义**
3. **关系语义**

当前 Agent 工具链已消费语义层输出。

---

# 二、当前实体与持久化表

## 1. 数据源
### 实体
- `Datasource`（包：`io.github.malonetalk.entity`）

### 表
- `datasource`

### 当前主要字段
- `id`, `name`, `type`, `host`, `port`, `database_name`, `username`, `password`
- `connection_url`, `status`, `test_status`, `description`, `creator_id`
- `create_time`, `update_time`

---

## 2. 表语义
### 实体
- `TableInfo`（包：`io.github.malonetalk.entity`）

### 表
- `table_info`

### 当前主要字段
- `id`, `table_name`, `table_description`, `domain`
- `datasource_id`, `is_active`, `is_visible`
- `create_time`, `update_time`

### 当前唯一键 / 索引
- `uk_datasource_table(datasource_id, table_name)`
- `idx_datasource_id`, `idx_is_active`, `idx_is_visible`
- `idx_datasource_active`, `idx_datasource_active_visible`
- `idx_datasource_visible_table(datasource_id, is_active, is_visible, table_name)`

---

## 3. 列语义
### 实体
- `ColumnSemanticInfo`（包：`io.github.malonetalk.entity`）

### 表
- `column_info`

### 当前主要字段
- `id`, `datasource_id`, `table_name`, `column_name`
- `column_description`, `is_active`, `is_visible`
- `create_time`, `update_time`

### 当前唯一键 / 索引
- `uk_datasource_table_column(datasource_id, table_name, column_name)`
- `idx_datasource_table_visible(datasource_id, table_name, is_active, is_visible)`
- `idx_datasource_table_visible_column(datasource_id, table_name, is_active, is_visible, column_name)`

---

## 4. 关系语义
### 实体
- `LogicalTableRelation`（包：`io.github.malonetalk.entity`）

### 表
- `logical_table_relation`

### 当前主要字段
- `id`, `datasource_id`
- `source_table_name`, `source_column_names_json`, `source_column_signature`
- `target_table_name`, `target_column_names_json`, `target_column_signature`
- `relation_type`, `description`, `is_enabled`
- `create_time`, `update_time`

### 当前唯一键 / 索引
- `uk_relation_source_signature(datasource_id, source_table_name, source_column_signature)`
- `idx_relation_source_table(datasource_id, source_table_name)`
- `idx_relation_source_enabled(datasource_id, source_table_name, is_enabled)`
- `idx_relation_source_enabled_id(datasource_id, source_table_name, is_enabled, id)`
- `idx_relation_source_target_id(datasource_id, source_table_name, target_table_name, id)`

---

# 三、当前 DTO 结构

## 1. 管理接口 DTO（`dto.semantic`）

### 表语义
- `TableSemanticUpdateRequest`
- `TableSemanticResponse`
- `BatchResetTableSemanticRequest`

### 列语义
- `ColumnSemanticUpdateRequest`
- `ColumnSemanticResponse`
- `BatchResetColumnSemanticRequest`

### 关系语义
- `BindLogicalTableRelationRequest`
- `UpdateLogicalTableRelationRequest`
- `UpdateLogicalTableRelationEnabledRequest`
- `BatchDeleteLogicalTableRelationRequest`
- `LogicalTableRelationResponse`
- `RelationCandidateTableResponse`
- `RelationCandidateColumnResponse`

### schema 聚合输出
- `TableSchemaSemanticPrompt`

---

## 2. AI / tool 输出 DTO（`dto.toolresponse`）
- `TableSemanticPrompt`
- `ColumnSemanticPrompt`
- `TableRelationToolResponse`

---

## 3. tool 协议 DTO（`dto.tool`）
- `ToolResult<T>`（`success`, `data`, `error`）
- `ToolError`（`code`, `message`）

---

# 四、当前 Controller 清单

## 1. 表语义管理
**类**: `TableSemanticController`（`/api/tableinfo/semantic`）

- `GET /tables?datasourceId=&page=&pageSize=&keywordPrefix=&sortOrder=` → `PageResponse<TableSemanticResponse>`
- `PUT /tables` → 更新表语义
- `DELETE /tables?datasourceId=&tableName=` → 重置单张表
- `DELETE /tables/batch` → 批量重置，返回重置行数

---

## 2. 列语义管理
**类**: `TableColumnSemanticController`（`/api/tableinfo/{tableName}/semantic/columns`）

- `GET ?datasourceId=&page=&pageSize=&keywordPrefix=&sortOrder=` → `PageResponse<ColumnSemanticResponse>`
- `PUT ?datasourceId=` → 更新列语义
- `DELETE ?datasourceId=&columnName=` → 重置单列
- `DELETE /batch` → 批量重置，返回重置行数

---

## 3. 关系语义管理
**类**: `TableRelationSemanticController`（`/api/tableinfo/semantic/relations`）

- `GET /candidate/tables?datasourceId=&page=&pageSize=&keywordPrefix=&sortOrder=`
- `GET /candidate/{tableName}/columns?datasourceId=&page=&pageSize=&keywordPrefix=&sortOrder=`
- `GET /{tableName}?datasourceId=&page=&pageSize=&keywordPrefix=&enabled=&sortOrder=`
- `POST /{tableName}?datasourceId=` → 创建，返回 `LogicalTableRelationResponse`
- `PUT /{tableName}/{relationId}?datasourceId=` → 更新，返回 `LogicalTableRelationResponse`
- `PUT /{tableName}/{relationId}/enabled?datasourceId=` → 启用/禁用，返回 `Boolean`
- `DELETE /{tableName}/{relationId}?datasourceId=` → 删除，返回 `Boolean`
- `DELETE /{tableName}/batch` → 批量删除，返回删除行数

---

## 4. Agent 接口
**类**: `AgentController`（`/api/agent`）

- `POST /chat` → 同步对话
- `POST /chat/stream` → SSE 流式对话
- `DELETE /session/{sessionId}` → 清除指定会话
- `DELETE /session` → 清除所有会话

---

# 五、当前 Service / Support 清单（`service.semantic` 包）

## 1. SemanticCatalog（核心合并组件）
**职责**: 请求级 `CatalogContext`，缓存合并后的表/列/关系，避免重复 IO

**关键子结构**:
- `CatalogContext`：持有单次请求的完整解析结果
- `ResolvedTable` / `ResolvedColumn` / `ResolvedRelation`：合并后的领域对象（records）
- `RelationState`：关系有效性判断结果

---

## 2. TableSemanticService（接口 + impl）
- `getTablePage(datasourceId, pageRequest, keywordPrefix, sortOrder)`
- `getAllTables()` / `getAllTables(datasourceId)`
- `getVisibleTablePrompts()` / `getVisibleTablePromptPage(pageRequest)`
- `updateTableSemantic(request)`
- `resetTableSemantic(datasourceId, tableName)`
- `resetTableSemantics(datasourceId, tableNames)` → 返回 int（批量重置行数）
- `getTableSchema(tableName)` / `getTableSchema(tableName, columnPageRequest, relationPageRequest)`

---

## 3. ColumnSemanticService（接口 + impl）
- `getColumnPage(datasourceId, tableName, pageRequest, keywordPrefix, sortOrder)`
- `getAllColumns(datasourceId, tableName)`
- `getVisibleColumnPrompts(datasourceId, tableName)`
- `updateColumnSemantic(datasourceId, tableName, request)`
- `resetColumnSemantic(datasourceId, tableName, columnName)`
- `resetColumnSemantics(datasourceId, tableName, columnNames)` → 返回 int（批量重置行数）

---

## 4. SemanticSchemaService（聚合门面）
**职责**: 组合 `TableSemanticService` 和 `ColumnSemanticService`，提供统一门面

**当前 Controller 层直接注入子服务，`SemanticSchemaService` 目前供扩展和测试使用**

---

## 5. SemanticVisibilitySupport
**核心能力**:
- `createVisibilityContext(datasource)` → `VisibilityContext`
- `mergeTables(datasource)` / `findMergedTable(datasource, tableName)`
- `mergeColumns(datasource, tableName)` / `findMergedColumn(datasource, tableName, columnName)`
- `isTableVisible(semanticTable, physicalTable)` / `isColumnVisible(semanticColumn, physicalColumn)`
- `resolveTableName(snapshot)` / `normalizeName(value)`

**Merge 规则**:
- 物理表/列作为基底，语义 metadata 覆盖描述与可见性
- 语义优先：有语义描述则用语义，无则用物理兜底

---

## 6. LogicalTableRelationService（接口 + impl）
- `listByDatasourceIdAndTable(datasourceId, tableName, pageRequest, keywordPrefix, enabled, sortOrder)`
- `create` / `update` / `updateEnabled` / `delete` / `deleteBatch`
- `listCandidateTables` / `listCandidateColumns`

**有效性判定**:
- source/target table & columns 可见性校验
- `enabled` 状态
- `effective` / `invalidReason` 计算（由 `SemanticCatalog.RelationState` 承载）

---

## 7. ColumnSemanticInfoService / TableInfoService（数据访问层）
- 均在 `service.semantic` 包下
- 实现在 `service.semantic.impl` 包下
- 封装 MyBatis Mapper 的 CRUD

---

## 8. SemanticMetadataSupportService / SemanticPageService
- `SemanticMetadataSupportService`：处理语义元数据 upsert 逻辑
- `SemanticPageService`：分页计算通用逻辑

---

## 9. LogicalTableRelationSupport（工具类，在 `service` 包）
**常量**:
- `RELATION_TYPE_FOREIGN_KEY = "foreign_key"`
- `RELATION_SOURCE_PHYSICAL = "physical"`
- `RELATION_SOURCE_LOGICAL = "logical"`

**核心能力**: 列名 JSON 编解码（`toJson` / `fromJson`）、列签名构建（`buildColumnSignature`）、表名规范化

---

## 10. ActiveDatasourceSupport / ActiveDatasourceLockManager（在 `service` 包）
- `ActiveDatasourceSupport.getActiveDatasource()`：当前取第一个 active 数据源
- `ActiveDatasourceLockManager`：防止并发激活同一数据源

---

# 六、当前 SchemaReader 能力

## 已具备
- 读取物理表（TTL 60s 内存缓存）
- 读取物理列及主键（TTL 60s 内存缓存）
- 读取物理外键，支持复合外键列聚合（`ImportedKeyAggregate`）
- `invalidateCache(datasourceId)` → 主动失效指定数据源的所有缓存

## 当前输出模型
- `ColumnInfo`（列名、类型、长度、nullable、defaultValue、isPrimaryKey、remarks）
- `TableRelationInfo`（源表、源列列表、目标表、目标列列表、关系类型、描述）
- `TableInfo`（复用于物理表映射）

---

# 七、当前 Agent 工具链

## 1. 已注册工具
- `GetTablesTool` → 直接依赖 `TableSemanticService`
- `GetTableSchemaTool` → 直接依赖 `TableSemanticService`
- `ExecuteSqlTool` → 依赖 `ActiveDatasourceSupport` + `SqlExecutor`

## 2. 当前工具协议
统一返回 `ToolResult<T>`：
- `success`
- `data`
- `error`（`ToolError`：`code` + `message`）

## 3. 当前 Agent 约束（System Prompt）
1. 先调用 `get_tables`，分页检查 `hasNext` / `totalPages`
2. 再调用 `get_table_schema`，列和关系各自分页检查
3. 再生成 SELECT SQL
4. 再调用 `execute_sql`
5. 最后回答

并要求只在 `success=true` 时继续推理，`maxIters=16`。

---

# 八、当前 AI 实际可见的语义信息

## 1. 表摘要（`get_tables`，`TableSemanticPrompt`）
- `name`, `domain`, `description`

## 2. 表详情（`get_table_schema`，`TableSchemaSemanticPrompt`）
- 表元信息：`name`, `domain`, `description`
- `columns`（`PageResponse<ColumnSemanticPrompt>`）：`name`, `type`, `primaryKey`, `nullable`, `defaultValue`, `description`
- `relations`（`PageResponse<TableRelationToolResponse>`）：`relationType`, `source`, `sourceTableName`, `sourceColumnNames`, `targetTableName`, `targetColumnNames`, `description`

---

# 九、当前已知外围现状

## 1. 配置
- `application.properties` 仍存在明文数据库账号密码
- 仍开启 `StdOutImpl` SQL 日志

## 2. 构建
- `pom.xml` 已包含：Spring Boot Web、Validation、MyBatis、MySQL Driver、AgentScope、Spring Boot Test、Spotless

## 3. CI
- `.github/workflows/maven.yml` 执行 `mvn verify --file data-agent-backend/pom.xml`

## 4. 测试目录
- `data-agent-backend/src/test` 不存在

## 5. 前端接入
- `data-agent-frontend/README.md` 为空，无实际前端代码

---

# 十、当前事实性缺失项（不做评价）

以下能力当前代码中未见落地：
- 字段 display name、semantic type、time role、enum schema、example values
- 指标语义层（metric / measure / dimension）
- 审计日志、版本历史、草稿/发布态、软删除/回滚
- 导入导出、completeness 统计
- 测试目录与自动化用例
- Agent 流式事件类型化输出
- 显式 datasource 路由
- 数据源健康检查与连通性测试

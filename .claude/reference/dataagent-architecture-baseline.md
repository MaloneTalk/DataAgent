# DataAgent 架构事实基线

生成时间：2026-05-12  
**最后更新：2026-05-17**  
范围：DataAgent 全局架构的代码事实快照  
用途：作为"DataAgent 架构是什么"的唯一事实来源，不包含判断与建议

---

## 一、模块边界

### 1. 管理库 vs 业务库
这是当前最重要的边界。

#### 管理库（Spring 主数据源）
- 配置在 `application.properties` 的 `spring.datasource.*`
- 用于存储平台自身管理数据：
  - `datasource` 表：数据源配置
  - `table_info` 表：表语义
  - `column_info` 表：列语义
  - `logical_table_relation` 表：逻辑表关系
  - `agentscope_sessions` 表：Agent 会话持久化

#### 业务库（动态数据源）
- 来自 `Datasource` 实体记录
- 由 `DynamicDataSourceManager` 按 datasource id 建 Hikari 连接池
- 用于：
  - Schema introspection (`SchemaReader`)
  - 执行用户查询 (`SqlExecutor`)

### 2. Physical schema vs Semantic layer
- **Physical schema**：`SchemaReader` 通过 JDBC metadata 读取，带 TTL 60 秒内存缓存
- **Semantic metadata**：`TableInfoService` / `ColumnSemanticInfoService` 管理（均在 `service.semantic` 包）
- **Merge & visibility**：`SemanticVisibilitySupport` 生成合并快照，`SemanticCatalog` 做请求级缓存与解析
- **对 Agent 暴露**：`TableSemanticService` / `GetTablesTool` 和 `GetTableSchemaTool` 消费

### 3. Physical relation vs Logical relation
- **Physical relation**：JDBC `getImportedKeys` 读取外键，支持复合外键聚合
- **Logical relation**：平台自定义存储的 `LogicalTableRelation`
- **最终合并**：在 `SemanticCatalog.CatalogContext.listVisibleRelations()` 里合并，物理关系优先，逻辑关系不重复添加

---

## 二、API 入口

### 1. Agent 聊天接口
**Controller**: `io.github.malonetalk.controller.AgentController`

**路径**:
- `POST /api/agent/chat` → 同步对话
- `POST /api/agent/chat/stream` → SSE 流式对话
- `DELETE /api/agent/session/{sessionId}` → 清理指定会话
- `DELETE /api/agent/session` → 清理所有会话

**请求**: `ChatRequest` (sessionId, message)

**响应**:
- 同步：`Result<String>`
- 流式：`Flux<String>` (当前为文本流，未结构化事件分类)

### 2. 数据源管理接口
**Controller**: `io.github.malonetalk.controller.DatasourceController`

**路径**:
- `GET /api/datasource` → 列出所有数据源
- `GET /api/datasource/{id}` → 查询单个数据源
- `POST /api/datasource` → 创建数据源
- `PUT /api/datasource` → 更新数据源（body 含 id）
- `DELETE /api/datasource/{id}` → 删除数据源
- `GET /api/datasource/status/{status}` → 按状态过滤
- `GET /api/datasource/type/{type}` → 按类型过滤

### 3. 表语义管理接口
**Controller**: `io.github.malonetalk.controller.TableSemanticController`

**路径**:
- `GET /api/tableinfo/semantic/tables?datasourceId=&page=&pageSize=&keywordPrefix=&sortOrder=` → 分页查询
- `PUT /api/tableinfo/semantic/tables` → 更新表语义
- `DELETE /api/tableinfo/semantic/tables?datasourceId=&tableName=` → 重置单张表
- `DELETE /api/tableinfo/semantic/tables/batch` → 批量重置

### 4. 列语义管理接口
**Controller**: `io.github.malonetalk.controller.TableColumnSemanticController`

**路径**:
- `GET /api/tableinfo/{tableName}/semantic/columns?datasourceId=&page=&pageSize=&keywordPrefix=&sortOrder=` → 分页查询
- `PUT /api/tableinfo/{tableName}/semantic/columns?datasourceId=` → 更新列语义
- `DELETE /api/tableinfo/{tableName}/semantic/columns?datasourceId=&columnName=` → 重置单列
- `DELETE /api/tableinfo/{tableName}/semantic/columns/batch` → 批量重置

### 5. 关系语义管理接口
**Controller**: `io.github.malonetalk.controller.TableRelationSemanticController`

**路径**:
- `GET /api/tableinfo/semantic/relations/candidate/tables?datasourceId=&page=&pageSize=&keywordPrefix=&sortOrder=` → 候选表
- `GET /api/tableinfo/semantic/relations/candidate/{tableName}/columns?datasourceId=&page=&pageSize=&keywordPrefix=&sortOrder=` → 候选列
- `GET /api/tableinfo/semantic/relations/{tableName}?datasourceId=&page=&pageSize=&keywordPrefix=&enabled=&sortOrder=` → 查询关系
- `POST /api/tableinfo/semantic/relations/{tableName}?datasourceId=` → 创建关系
- `PUT /api/tableinfo/semantic/relations/{tableName}/{relationId}?datasourceId=` → 更新关系
- `PUT /api/tableinfo/semantic/relations/{tableName}/{relationId}/enabled?datasourceId=` → 启用/禁用
- `DELETE /api/tableinfo/semantic/relations/{tableName}/{relationId}?datasourceId=` → 删除关系
- `DELETE /api/tableinfo/semantic/relations/{tableName}/batch` → 批量删除

---

## 三、Agent 编排

### 核心服务
**类**: `io.github.malonetalk.agent.AgentService`

**职责**:
- 创建 ReActAgent（每次对话新建实例）
- 注册工具 (`get_tables`, `get_table_schema`, `execute_sql`)
- 管理会话（内存缓存 `ConcurrentHashMap` + MySQL 持久化）
- 同步对话 / 流式对话
- Session key 前缀：`data-agent:`

### Agent 配置
**System Prompt 核心要求**:
1. 先调用 `get_tables`，获取可见表列表
2. 检查分页数据 (`hasNext`, `totalPages`, `items`)，必要时翻页
3. 根据用户问题选择相关表，调用 `get_table_schema`
4. 检查 `columns` 和 `relations` 分页，必要时翻页
5. 基于表结构生成 SELECT SQL
6. 调用 `execute_sql` 执行 SQL
7. 根据查询结果回答用户问题

**工具返回协议**:
- 统一返回 `success / data / error` 三段结构
- 必须先检查 `success`
- 只有 `success=true` 时才能使用 `data`
- 如果 `success=false`，必须读取 `error.code` 和 `error.message`

**SQL 约束**:
- 只允许 SELECT
- 生成 SQL 前必须先查看表结构
- 不能跳过失败直接猜测表结构

**maxIters**: 16

### 模型配置
**类**: `io.github.malonetalk.agent.ModelFactory`

**支持 Provider**:
- `dashscope` (默认)
- `openai`
- `anthropic`
- `ollama`

**配置来源**: `application.properties`
- `agentscope.model.provider`
- `agentscope.model.name`
- `agentscope.model.base-url`
- `agentscope.api.key`

**默认配置**:
- Provider: `dashscope`
- Model: `qwen3-max`

---

## 四、Agent 工具链

### 1. GetTablesTool
**工具名**: `get_tables`  
**类**: `io.github.malonetalk.agent.tools.GetTablesTool`  
**直接依赖**: `TableSemanticService`

**功能**: 返回当前激活数据源的可见表分页列表

**输入参数**:
- `page`：可选，默认 1
- `page_size`：可选，默认 20，最大 100

**输出字段** (`TableSemanticPrompt`):
- `name`: 表名
- `domain`: 业务域
- `description`: 表描述（语义优先，物理兜底）

**分页协议**: `PageResponse<TableSemanticPrompt>`

### 2. GetTableSchemaTool
**工具名**: `get_table_schema`  
**类**: `io.github.malonetalk.agent.tools.GetTableSchemaTool`  
**直接依赖**: `TableSemanticService`

**功能**: 返回指定表的语义化结构，列与关系各自独立分页

**输入参数**:
- `table_name`：表名
- `column_page` / `column_page_size`：列分页
- `relation_page` / `relation_page_size`：关系分页

**输出字段** (`TableSchemaSemanticPrompt`):
- 表元信息：`name`, `domain`, `description`
- `columns`：`PageResponse<ColumnSemanticPrompt>` (name, type, primaryKey, nullable, defaultValue, description)
- `relations`：`PageResponse<TableRelationToolResponse>` (relationType, source, sourceTableName, sourceColumnNames, targetTableName, targetColumnNames, description)

### 3. ExecuteSqlTool
**工具名**: `execute_sql`  
**类**: `io.github.malonetalk.agent.tools.ExecuteSqlTool`  
**直接依赖**: `ActiveDatasourceSupport`, `SqlExecutor`

**功能**: 在激活数据源上执行 SELECT，返回结构化结果

**约束**:
- 只允许 SELECT
- 最多返回 200 行
- 超时 30 秒

---

## 五、语义层服务（`service.semantic` 包）

### 1. SemanticCatalog（核心合并组件）
**类**: `io.github.malonetalk.service.semantic.SemanticCatalog`

**职责**: 生成请求级 `CatalogContext`，缓存合并结果，避免重复 IO

**内部类**:
- `CatalogContext`：请求级缓存上下文，持有已解析的表、列、关系
- `ResolvedTable` (record)：合并后的表，包含物理表、语义表、可见性
- `ResolvedColumn` (record)：合并后的列，包含物理列、语义列、可见性
- `ResolvedRelation` (record)：合并后的关系（物理 + 逻辑去重）
- `RelationState` (record)：关系有效性判断结果

**关键方法**:
- `createContext(datasource)` → 创建 `CatalogContext`
- `CatalogContext.tables()` → 所有表
- `CatalogContext.columns(tableName)` → 某表所有列
- `CatalogContext.listVisibleRelations(tableName)` → 某表可见关系（物理+逻辑合并去重）
- `CatalogContext.evaluateRelationState(...)` → 关系有效性校验
- `CatalogContext.validateVisibleTable(...)` / `validateVisibleColumns(...)` → 可见性断言

### 2. TableSemanticService / TableSemanticServiceImpl
**接口**: `io.github.malonetalk.service.semantic.TableSemanticService`  
**实现**: `io.github.malonetalk.service.semantic.impl.TableSemanticServiceImpl`

**核心能力**:
- `getTablePage(datasourceId, pageRequest, keywordPrefix, sortOrder)` → 分页+关键字+排序
- `getAllTables()` / `getAllTables(datasourceId)` → 全量列表
- `getVisibleTablePrompts()` / `getVisibleTablePromptPage(pageRequest)` → Agent prompt 输出
- `updateTableSemantic(request)` → 更新
- `resetTableSemantic(datasourceId, tableName)` → 重置单张
- `resetTableSemantics(datasourceId, tableNames)` → 批量重置
- `getTableSchema(tableName)` / `getTableSchema(tableName, columnPageRequest, relationPageRequest)` → 单表 schema

### 3. ColumnSemanticService / ColumnSemanticServiceImpl
**接口**: `io.github.malonetalk.service.semantic.ColumnSemanticService`  
**实现**: `io.github.malonetalk.service.semantic.impl.ColumnSemanticServiceImpl`

**核心能力**:
- `getColumnPage(datasourceId, tableName, pageRequest, keywordPrefix, sortOrder)` → 分页+关键字+排序
- `getAllColumns(datasourceId, tableName)` → 全量列表
- `getVisibleColumnPrompts(datasourceId, tableName)` → Agent prompt 输出
- `updateColumnSemantic(datasourceId, tableName, request)` → 更新
- `resetColumnSemantic(datasourceId, tableName, columnName)` → 重置单列
- `resetColumnSemantics(datasourceId, tableName, columnNames)` → 批量重置

### 4. SemanticSchemaService（聚合门面）
**类**: `io.github.malonetalk.service.semantic.SemanticSchemaService`

**职责**: 组合 `TableSemanticService` 和 `ColumnSemanticService`，提供统一门面（当前 Controller 层未直接使用，工具层直接依赖子服务）

### 5. SemanticVisibilitySupport
**类**: `io.github.malonetalk.service.semantic.SemanticVisibilitySupport`

**核心能力**:
- `createVisibilityContext(datasource)` → 创建 `VisibilityContext`
- `mergeTables(datasource)` / `mergeColumns(datasource, tableName)` → 合并物理与语义数据
- `isTableVisible(semanticTable, physicalTable)` → 可见性判断
- `isColumnVisible(semanticColumn, physicalColumn)` → 可见性判断
- `resolveTableName(snapshot)` / `normalizeName(value)` → 名称处理

### 6. LogicalTableRelationService / LogicalTableRelationServiceImpl
**接口**: `io.github.malonetalk.service.semantic.LogicalTableRelationService`  
**实现**: `io.github.malonetalk.service.semantic.impl.LogicalTableRelationServiceImpl`

**核心能力**:
- `listByDatasourceIdAndTable(datasourceId, tableName, pageRequest, keywordPrefix, enabled, sortOrder)`
- `create(datasourceId, tableName, request)` → 创建关系
- `update(datasourceId, tableName, relationId, request)` → 更新关系
- `updateEnabled(datasourceId, tableName, relationId, enabled)` → 启用/禁用
- `delete(datasourceId, tableName, relationId)` → 删除
- `deleteBatch(datasourceId, tableName, relationIds)` → 批量删除
- `listCandidateTables(datasourceId, pageRequest, keywordPrefix, sortOrder)` → 候选表
- `listCandidateColumns(datasourceId, tableName, pageRequest, keywordPrefix, sortOrder)` → 候选列

### 7. ColumnSemanticInfoService / TableInfoService（数据访问层）
**接口**:
- `io.github.malonetalk.service.semantic.ColumnSemanticInfoService`
- `io.github.malonetalk.service.semantic.TableInfoService`

**实现**:
- `io.github.malonetalk.service.semantic.impl.ColumnSemanticInfoServiceImpl`
- `io.github.malonetalk.service.semantic.impl.TableInfoServiceImpl`

**职责**: 封装 MyBatis Mapper 的数据库 CRUD 操作

### 8. SemanticMetadataSupportService / SemanticPageService
**接口**:
- `io.github.malonetalk.service.semantic.SemanticService`
- `io.github.malonetalk.service.semantic.SemanticPageService`

**实现**:
- `io.github.malonetalk.service.semantic.impl.SemanticServiceImpl`
- `io.github.malonetalk.service.semantic.impl.SemanticPageServiceImpl`

**职责**: 分别处理语义元数据写入（upsert）和分页计算逻辑

### 9. LogicalTableRelationSupport（工具类）
**类**: `io.github.malonetalk.service.semantic.relation.LogicalTableRelationSupport`

**常量**:
- `RELATION_TYPE_FOREIGN_KEY = "foreign_key"`
- `RELATION_SOURCE_PHYSICAL = "physical"`
- `RELATION_SOURCE_LOGICAL = "logical"`

**核心能力**: 列名 JSON 编解码、列签名构建、表名规范化

### 10. ActiveDatasourceSupport
**类**: `io.github.malonetalk.service.ActiveDatasourceSupport`

**当前策略**: 多个 active 数据源时直接取第一个（存在跨库误查风险）

---

## 六、动态数据源与 SQL 执行

### 1. 动态数据源管理
**类**: `io.github.malonetalk.agent.datasource.DynamicDataSourceManager`

**核心能力**: 按 datasource id 按需创建 Hikari 连接池，管理业务库连接

### 2. Schema 读取
**类**: `io.github.malonetalk.agent.datasource.SchemaReader`

**核心能力**:
- 读取物理表列表（TTL 60s 缓存）
- 读取物理列（TTL 60s 缓存）
- 读取主键
- 读取物理外键，支持复合外键聚合（`ImportedKeyAggregate`）
- `invalidateCache(datasourceId)` → 删除/更新数据源时主动失效缓存

**缓存实现**: `ConcurrentHashMap<Key, CacheEntry<V>>`（`CacheEntry` 含 `expireAtMillis`）

**输出模型**: `ColumnInfo`, `TableRelationInfo`, `TableInfo`

### 3. SQL 执行
**类**: `io.github.malonetalk.agent.datasource.SqlExecutor`

**约束**:
- 只允许 SELECT
- 拒绝 DML/DDL
- 最多返回 200 行
- 查询超时 30 秒

---

## 七、配置与依赖

### 1. 主配置
**路径**: `data-agent-backend/src/main/resources/application.properties`

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/data_agent?...
spring.datasource.username=root
spring.datasource.password=root

agentscope.model.provider=dashscope
agentscope.model.name=qwen3-max
agentscope.model.base-url=
agentscope.api.key=${DASHSCOPE_API_KEY:}

spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
mybatis.mapper-locations=classpath:mapper/*.xml
mybatis.configuration.log-impl=org.apache.ibatis.logging.stdout.StdOutImpl
```

### 2. 依赖入口
**路径**: `data-agent-backend/pom.xml`

**关键依赖**:
- Spring Boot 4.0.3（Java 21）
- `spring-boot-starter-web` + `spring-boot-starter-validation`
- `mybatis-spring-boot-starter` 4.0.0
- `mysql-connector-j`
- `io.agentscope:agentscope`
- Spotless + google-java-format

---

## 八、分页协议

### 统一分页结构
**类**: `io.github.malonetalk.dto.pagination.PageResponse<T>`

**字段**: `page`, `pageSize`, `total`, `totalPages`, `hasPrevious`, `hasNext`, `items`

**限制**: 每页最大 100 条（`PageRequest.MAX_PAGE_SIZE`）

**Agent prompt 要求**:
- 必须检查 `hasNext`、`totalPages`、`items`
- 不得把单页数据误当成全量数据
- 当分页数据不足以支撑结论时，必须显式继续翻页

---

## 九、工具返回协议

**类**: `io.github.malonetalk.common.ToolResult<T>` / `io.github.malonetalk.common.ToolError`

**字段**: `success`, `data`, `error`（`error` 含 `code`, `message`）

**Agent 消费规则**:
1. 必须先检查 `success`
2. 只有 `success=true` 时才能使用 `data`
3. 如果 `success=false`，必须读取 `error.code` 和 `error.message`
4. 不能把 `error` 当成正常数据继续生成 SQL

---

## 十、会话管理

**Session 存储**: `MysqlSession`（数据库 `data_agent`，表 `agentscope_sessions`）  
**Session key 前缀**: `data-agent:`  
**内存缓存**: `ConcurrentHashMap<String, Session>`

**操作**:
- `chat()` / `chatStream()` → 自动加载/保存会话
- `clearSession(sessionId)` → 清理指定会话（内存 + DB）
- `clearAllSessions()` → 清理所有受管会话（内存 + DB）

---

## 十一、当前已知限制

### 1. 流式输出
- 当前为 `Flux<String>` 文本流
- 未结构化事件分类（reasoning / tool result / final）
- 前端无法区分事件类型

### 2. 数据源路由
- 多个 active 数据源时直接取第一个
- 会话无法显式绑定 datasource
- 存在跨库误查风险

### 3. SQL 执行
- 最多返回 200 行
- 超时 30 秒
- 无异步任务模型
- 无查询取消能力

### 4. 测试
- `src/test` 目录不存在
- 核心链路缺自动化回归保护

### 5. 前端
- `data-agent-frontend` 基本为空（只有 README）

---

## 十二、关键代码路径速查

### Agent 主链路
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/AgentService.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/ModelFactory.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/controller/AgentController.java`

### Agent 工具
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/tools/GetTablesTool.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/tools/GetTableSchemaTool.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/tools/ExecuteSqlTool.java`

### 语义层核心
- `data-agent-backend/src/main/java/io/github/malonetalk/service/semantic/SemanticCatalog.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/service/semantic/SemanticVisibilitySupport.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/service/semantic/SemanticSchemaService.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/service/semantic/impl/TableSemanticServiceImpl.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/service/semantic/impl/ColumnSemanticServiceImpl.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/service/semantic/impl/LogicalTableRelationServiceImpl.java`

### 数据源与执行
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/datasource/DynamicDataSourceManager.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/datasource/SchemaReader.java`
- `data-agent-backend/src/main/java/io/github/malonetalk/agent/datasource/SqlExecutor.java`

### 配置与依赖
- `data-agent-backend/src/main/resources/application.properties`
- `data-agent-backend/pom.xml`

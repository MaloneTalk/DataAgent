# DataAgent 自动化测试体系实现方案

生成时间：2026-05-17  
调研来源：JUnit5 + Mockito 最佳实践、MyBatis Spring Boot Test 官方文档、Spider/BIRD NL2SQL 评估方法、AgentScope Java 可测性分析

> 前置：项目当前 `src/test` 目录不存在，`pom.xml` 已有 `spring-boot-starter-test`，需从零建立测试体系。

---

## 一、分层测试策略总览

| 层次 | 推荐方式 | 理由 |
|---|---|---|
| 语义合并纯逻辑（SemanticCatalog、SemanticVisibilitySupport） | Mockito 纯单元测试 | 核心逻辑全在内存，无 DB 操作，Mock Mapper/SchemaReader 即可 |
| Mapper 层（TableInfoMapper、ColumnSemanticInfoMapper、LogicalTableRelationMapper） | `@MybatisTest` + H2 | 验证 XML SQL 正确性，H2 最轻量 |
| 业务服务层（LogicalTableRelationServiceImpl、TableSemanticServiceImpl 等） | Mockito 单元测试 Mock Mapper | 纯业务逻辑，不测 SQL |
| Agent 工具层（GetTablesTool、GetTableSchemaTool、ExecuteSqlTool） | Mockito Mock 依赖 Service | 工具是 POJO 化纯函数，不需要 LLM 调用 |
| SchemaReader（JDBC DatabaseMetaData） | H2 内存库集成测试 | 依赖真实 JDBC Connection，H2 外键可用 |
| ReActAgent 全链路 | 不建议 CI 覆盖，用 WireMock VCR 冒烟测试 | AgentScope Model 接口无公开 Fake 实现，性价比低 |

---

## 二、NL2SQL 测试评估维度（行业标准）

Spider / BIRD 基准定义的四个评估维度：

1. **Exact Set Match（ESM）**：分解 SQL 子句做集合对比，有假阴性（等价 SQL 格式不同会判错）
2. **Execution Accuracy（EA）**：执行预测 SQL 和 gold SQL，比较结果集，Spider 官方首选指标
3. **SQL 语法合法性**：最低保障，可用 JSqlParser 或 H2 `parse-only` 检验
4. **答案正确性**：执行后比较最终回答内容，适合有 golden answer 的场景

**本项目推荐优先实现**：EA（H2 执行预期 SQL 与生成 SQL 结果集对比）+ 语法合法性。

自建 NL2SQL fixture 格式（存 `src/test/resources/nl2sql_fixtures/`）：
```json
[
  {
    "id": "agg-001",
    "question": "统计用户总数",
    "expected_sql": "SELECT COUNT(*) FROM users",
    "difficulty": "easy",
    "category": "aggregation",
    "tables": ["users"]
  },
  {
    "id": "join-001",
    "question": "查询最近7天有订单的用户姓名",
    "expected_sql": "SELECT DISTINCT u.name FROM users u JOIN orders o ON u.id = o.user_id WHERE o.created_at >= CURRENT_DATE - INTERVAL 7 DAY",
    "difficulty": "medium",
    "category": "join",
    "tables": ["users", "orders"]
  }
]
```

---

## 三、pom.xml 需新增的测试依赖

```xml
<!-- H2 内存数据库，用于 Mapper 集成测试 + SchemaReader 测试 -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>

<!-- MyBatis 官方 Test Autoconfigure，提供 @MybatisTest 注解 -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter-test</artifactId>
    <version>4.0.0</version>
    <scope>test</scope>
</dependency>
```

`spring-boot-starter-test` 已包含 JUnit5 + Mockito + AssertJ，无需额外添加。

---

## 四、application-test.properties 配置

```properties
# H2 开启 MySQL 兼容模式（解决 ON DUPLICATE KEY UPDATE、反引号标识符等问题）
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

mybatis.mapper-locations=classpath:mapper/*.xml
mybatis.configuration.map-underscore-to-camel-case=true
```

---

## 五、H2 与 MySQL 兼容性坑点汇总

| 坑点 | 症状 | 解决方案 |
|---|---|---|
| `ON DUPLICATE KEY UPDATE` | SQL syntax error | JDBC URL 添加 `;MODE=MySQL` |
| MySQL 的 `LIMIT m, n` 语法 | SQL syntax error | 改为 `LIMIT n OFFSET m`（H2 2.x MySQL 模式支持） |
| 反引号标识符 `` `table` `` | SQL syntax error | 开启 MySQL 模式，或 schema-h2.sql 不加反引号 |
| `TINYINT(1)` 当 Boolean | 类型映射错误 | schema-h2.sql 中改用 `BOOLEAN` 或 `BIT(1)` |
| `GROUP_CONCAT DISTINCT ORDER BY` | H2 1.4.200 有 bug | Spring Boot 4.x 的 H2 2.x 已修复 |
| `DatabaseMetaData.getTables()` catalog/schema | H2 catalog 是数据库名，schema 是 PUBLIC | 建表时不加 schema 前缀，或 JDBC URL 加 `;SCHEMA=PUBLIC` |
| `getImportedKeys()` 读不到 FK | 测试中外键关系读不到 | schema-h2-test.sql 中显式声明 `FOREIGN KEY` 约束 |

---

## 六、核心测试类骨架

### 6.1 SemanticVisibilitySupportTest（最高优先级）

```java
@ExtendWith(MockitoExtension.class)
class SemanticVisibilitySupportTest {

    @Mock TableInfoService tableInfoService;
    @Mock ColumnSemanticInfoService columnSemanticInfoService;
    @Mock SchemaReader schemaReader;

    SemanticVisibilitySupport support;
    Datasource datasource;

    @BeforeEach
    void setUp() {
        support = new SemanticVisibilitySupport(tableInfoService, columnSemanticInfoService, schemaReader);
        datasource = new Datasource();
        datasource.setId(1);
    }

    // Fixture 1：物理表有语义，语义配置可见
    @Test
    void mergeTables_physicalAndSemantic_semanticOverrides() {
        when(schemaReader.getTables(datasource)).thenReturn(List.of(physicalTable("orders")));
        when(tableInfoService.findByDatasourceId(1))
            .thenReturn(List.of(semanticTable("orders", true, "订单表")));

        var ctx = support.createVisibilityContext(datasource);
        var snapshots = ctx.mergeTables();

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).semanticTable().getTableDescription()).isEqualTo("订单表");
    }

    // Fixture 2：物理表无语义记录
    @Test
    void mergeTables_physicalOnly_semanticIsNull() {
        when(schemaReader.getTables(datasource)).thenReturn(List.of(physicalTable("users")));
        when(tableInfoService.findByDatasourceId(1)).thenReturn(Collections.emptyList());

        var snapshot = support.createVisibilityContext(datasource).mergeTables().get(0);
        assertThat(snapshot.semanticTable()).isNull();
    }

    // Fixture 3：语义记录对应的物理表已删除（孤儿语义）
    @Test
    void mergeTables_orphanSemantic_isDropped() {
        when(schemaReader.getTables(datasource)).thenReturn(List.of(physicalTable("orders")));
        when(tableInfoService.findByDatasourceId(1))
            .thenReturn(List.of(semanticTable("deleted_table", true, "已删除")));

        var snapshots = support.createVisibilityContext(datasource).mergeTables();
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).semanticTable()).isNull();
    }

    // Fixture 4：isVisible=false 的表不可见
    @Test
    void isTableVisible_semanticInvisible_returnsFalse() {
        TableInfo semantic = semanticTable("orders", false, "不可见表");
        assertThat(support.isTableVisible(semantic, physicalTable("orders"))).isFalse();
    }

    private TableInfo physicalTable(String name) {
        TableInfo t = new TableInfo();
        t.setTableName(name); t.setIsVisible(true); t.setIsActive(true);
        return t;
    }
    private TableInfo semanticTable(String name, boolean visible, String desc) {
        TableInfo t = new TableInfo();
        t.setTableName(name); t.setIsVisible(visible); t.setIsActive(true);
        t.setTableDescription(desc); t.setCreateTime(LocalDateTime.now());
        return t;
    }
}
```

### 6.2 SemanticCatalog.evaluateRelationState 分支测试矩阵

| 分支 | 测试场景 | 预期 effective | 预期 invalidReason |
|---|---|---|---|
| enabled=false | 关系被手动禁用 | false | "Relation is disabled." |
| sourceColumnErrorMessage != null | sourceColumnNames JSON 解析失败 | false | 解析错误信息 |
| source 表不存在 | source table 从未出现在物理层 | false | "Source table does not exist: ..." |
| source 表不可见 | 语义配置隐藏了 source 表 | false | "Source table is not visible: ..." |
| target 表不存在 | target table 已从数据库删除 | false | "Target table does not exist: ..." |
| source 列不存在 | source 列从物理表中删除 | false | "Source column does not exist: ..." |
| source 列不可见 | source 列被语义隐藏 | false | "Source column is not visible: ..." |
| 全部条件满足 | 正常关联，所有表列均存在且可见 | true | null |

### 6.3 GetTablesToolTest（工具层测试）

```java
@ExtendWith(MockitoExtension.class)
class GetTablesToolTest {

    @Mock TableSemanticService tableSemanticService;
    @InjectMocks GetTablesTool getTablesTool;

    @Test
    void getTables_success_returnsWrappedPage() {
        var page = PageResponse.of(
            List.of(new TableSemanticPrompt("users", "用户域", "用户信息表")),
            1L, PageRequest.of(1, 20));
        when(tableSemanticService.getVisibleTablePromptPage(any())).thenReturn(page);

        var result = getTablesTool.getTables(1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getItems()).hasSize(1);
        assertThat(result.getData().getItems().get(0).name()).isEqualTo("users");
    }

    @Test
    void getTables_whenIllegalArgument_returnsErrorResult() {
        when(tableSemanticService.getVisibleTablePromptPage(any()))
            .thenThrow(new IllegalArgumentException("page must be >= 1"));

        var result = getTablesTool.getTables(-1, 20);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError().getCode()).isEqualTo("INVALID_PAGINATION_ARGUMENT");
    }
}
```

### 6.4 ExecuteSqlToolTest

```java
@ExtendWith(MockitoExtension.class)
class ExecuteSqlToolTest {

    @Mock ActiveDatasourceSupport activeDatasourceSupport;
    @Mock SqlExecutor sqlExecutor;
    @InjectMocks ExecuteSqlTool executeSqlTool;

    @Test
    void executeSql_whenNoActiveDatasource_returnsNoDatasourceError() {
        when(activeDatasourceSupport.getActiveDatasource()).thenReturn(null);

        var result = executeSqlTool.executeSql("SELECT 1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError().getCode()).isEqualTo("NO_ACTIVE_DATASOURCE");
    }

    @Test
    void executeSql_whenSecurityException_returnsSqlSecurityError() {
        Datasource ds = new Datasource();
        when(activeDatasourceSupport.getActiveDatasource()).thenReturn(ds);
        when(sqlExecutor.execute(eq(ds), anyString()))
            .thenThrow(new SqlExecutor.SqlSecurityException("not allowed"));

        var result = executeSqlTool.executeSql("DELETE FROM users");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError().getCode()).isEqualTo("SQL_SECURITY_ERROR");
    }

    @Test
    void executeSql_success_returnsQueryResult() {
        Datasource ds = new Datasource();
        var qr = new QueryResult(List.of("id", "name"), List.of(List.of("1", "Alice")));
        when(activeDatasourceSupport.getActiveDatasource()).thenReturn(ds);
        when(sqlExecutor.execute(any(), eq("SELECT id, name FROM users"))).thenReturn(qr);

        var result = executeSqlTool.executeSql("SELECT id, name FROM users");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getColumns()).containsExactly("id", "name");
    }
}
```

### 6.5 SchemaReaderTest（H2 集成测试）

```java
@ExtendWith(MockitoExtension.class)
class SchemaReaderTest {

    @Mock DynamicDataSourceManager dynamicDataSourceManager;
    SchemaReader schemaReader;
    DataSource h2DataSource;

    @BeforeEach
    void setUp() throws Exception {
        h2DataSource = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:schema-h2-test.sql")
            .build();
        when(dynamicDataSourceManager.getOrCreateDataSource(any())).thenReturn(h2DataSource);
        schemaReader = new SchemaReader(dynamicDataSourceManager);
    }

    @Test
    void getTables_returnsTablesFromH2MetaData() {
        Datasource ds = new Datasource(); ds.setId(1);
        var tables = schemaReader.getTables(ds);
        assertThat(tables).isNotEmpty();
        assertThat(tables).anyMatch(t -> t.getTableName().equalsIgnoreCase("orders"));
    }

    @Test
    void getTableSchema_returnsColumnsWithPrimaryKeyFlag() {
        Datasource ds = new Datasource(); ds.setId(1);
        var columns = schemaReader.getTableSchema(ds, "ORDERS");
        assertThat(columns).anyMatch(c -> c.getColumnName().equalsIgnoreCase("id") && c.isPrimaryKey());
    }

    @Test
    void getImportedRelations_returnsForeignKeyRelation() {
        Datasource ds = new Datasource(); ds.setId(1);
        var relations = schemaReader.getImportedRelations(ds, "ORDER_ITEMS");
        assertThat(relations).anyMatch(r -> r.targetTableName().equalsIgnoreCase("orders"));
    }
}
```

`src/test/resources/schema-h2-test.sql`：
```sql
CREATE TABLE IF NOT EXISTS orders (
    id         INT NOT NULL PRIMARY KEY,
    user_id    INT NOT NULL,
    amount     DECIMAL(10,2),
    created_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS order_items (
    id       INT NOT NULL PRIMARY KEY,
    order_id INT NOT NULL,
    sku      VARCHAR(64),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
```

---

## 七、推荐测试目录结构

```
src/test/
├── java/io/github/malonetalk/
│   ├── agent/
│   │   ├── datasource/
│   │   │   └── SchemaReaderTest.java              # H2 集成测试
│   │   └── tools/
│   │       ├── GetTablesToolTest.java             # Mockito 单元
│   │       ├── GetTableSchemaToolTest.java        # Mockito 单元
│   │       └── ExecuteSqlToolTest.java            # Mockito 单元
│   ├── mapper/
│   │   ├── TableInfoMapperTest.java               # @MybatisTest + H2
│   │   ├── ColumnSemanticInfoMapperTest.java      # @MybatisTest + H2
│   │   └── LogicalTableRelationMapperTest.java    # @MybatisTest + H2
│   └── service/
│       └── semantic/
│           ├── SemanticVisibilitySupportTest.java  # Mockito 单元（最高优先级）
│           ├── SemanticCatalogTest.java            # Mockito 单元
│           ├── SemanticCatalogRelationStateTest.java  # 分支矩阵
│           └── impl/
│               ├── LogicalTableRelationServiceImplTest.java
│               ├── TableSemanticServiceImplTest.java
│               └── ColumnSemanticServiceImplTest.java
└── resources/
    ├── application-test.properties
    ├── schema-h2.sql                              # Mapper 测试建表（Mapper XML 兼容版）
    ├── schema-h2-test.sql                         # SchemaReader 测试建表（含 FK 约束）
    └── nl2sql_fixtures/
        └── basic_queries.json                     # NL2SQL 评估用例（未来扩展）
```

---

## 八、开工优先级建议

| 优先级 | 测试类 | 理由 |
|---|---|---|
| P0 | `SemanticVisibilitySupportTest` | merge 规则是整个语义层的核心，一旦出 bug 影响 AI 推理所有链路 |
| P0 | `SemanticCatalogRelationStateTest` | evaluateRelationState 分支多，回归风险高 |
| P0 | `ExecuteSqlToolTest` | SQL 执行工具有安全边界逻辑，必须有测试覆盖 |
| P1 | `GetTablesToolTest` / `GetTableSchemaToolTest` | 工具输出格式正确性 |
| P1 | `LogicalTableRelationServiceImplTest` | 有效性判定逻辑复杂 |
| P2 | `TableInfoMapperTest` 等 Mapper 层测试 | 验证 XML SQL，H2 兼容性是主要成本 |
| P3 | `SchemaReaderTest` | H2 MetaData 验证，覆盖 FK 聚合逻辑 |

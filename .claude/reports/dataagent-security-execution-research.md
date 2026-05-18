# DataAgent 数据源安全与执行治理实现方案

生成时间：2026-05-17  
调研来源：JSqlParser 4.9 文档、Apache Superset query_execution 表设计、HikariCP 连接验证文档、Jasypt Spring Boot 集成、Java AES-256/GCM 标准实现

---

## 一、SQL 安全执行边界增强

### 1.1 引入 JSqlParser

**推荐 JSqlParser 4.9，替换现有正则校验。** 正则无法处理 CTE、多语句分隔符、MySQL 版本注释注入等语义边界，AST 级别是唯一可靠方案。

```xml
<!-- pom.xml 新增 -->
<dependency>
    <groupId>com.github.jsqlparser</groupId>
    <artifactId>jsqlparser</artifactId>
    <version>4.9</version>
</dependency>
```

### 1.2 边界情况处理结论

| 场景 | JSqlParser 处理 | 结论 |
|---|---|---|
| CTE `WITH ... AS (...)` | 解析为 `PlainSelect.withItemsList`，顶层仍是 `Select` 实例 | 安全，正确识别 |
| 子查询中的 DML | MySQL 不支持此语法，JSqlParser 在 parse 阶段抛异常 | 安全，解析失败直接拒绝 |
| MySQL 版本注释 `/*!32302 DROP*/SELECT ...` | JSqlParser 剥离注释，但 **MySQL JDBC 驱动会忠实传递原始 SQL**，MySQL 会执行注释中的 DROP | **需要前置正则拦截 `/*!`** |
| 多语句 `SELECT 1; DROP TABLE x` | `CCJSqlParserUtil.parse()`（单语句模式）抛异常 | 安全 |
| `SELECT ... INTO OUTFILE` | 解析为 `Select`，但 `PlainSelect.getIntoTables()` 非空 | 需要 AST 检查 |
| `SLEEP()`、`BENCHMARK()` 等时间盲注 | 解析为合法 `Select`，需 AST 遍历 Function 节点 | 需要函数黑名单检查 |

### 1.3 推荐两层校验（性价比最高）

**第1层**：正则快速拒绝（保留现有逻辑 + 新增 `/*!` 拦截）  
**第2层**：JSqlParser `instanceof Select` 校验 + `INTO OUTFILE` 检查

第3层（AST 函数黑名单）按合规需要选配。

### 1.4 SqlExecutor.java 改造骨架

现有 `validateSql()` 方法替换为：

```java
// 新增到 pom.xml：jsqlparser 4.9
private static final Pattern MYSQL_VERSION_COMMENT =
    Pattern.compile("/\\*!", Pattern.CASE_INSENSITIVE);

private void validateSql(String sql) {
    if (sql == null || sql.isBlank()) {
        throw new SqlSecurityException("SQL must not be empty");
    }
    // 第1层：快速拒绝（保留现有关键字黑名单逻辑，追加版本注释拦截）
    if (MYSQL_VERSION_COMMENT.matcher(sql).find()) {
        throw new SqlSecurityException("MySQL version comments are not allowed");
    }
    // 第2层：JSqlParser AST 校验
    Statement statement;
    try {
        statement = CCJSqlParserUtil.parse(sql);
    } catch (Exception e) {
        throw new SqlSecurityException("SQL parse failed: " + e.getMessage());
    }
    if (!(statement instanceof Select)) {
        throw new SqlSecurityException(
            "Only SELECT queries are allowed. Got: "
            + statement.getClass().getSimpleName());
    }
    // INTO OUTFILE 检查
    if (statement instanceof PlainSelect ps
            && ps.getIntoTables() != null
            && !ps.getIntoTables().isEmpty()) {
        throw new SqlSecurityException("INTO OUTFILE is not allowed");
    }
}

// 可选第3层：函数黑名单
private static final Set<String> FORBIDDEN_FUNCTIONS =
    Set.of("SLEEP", "BENCHMARK", "LOAD_FILE");

private void checkForbiddenFunctions(Select select) {
    // 用 StatementDeParser Visitor 遍历 Function 节点
    // 检查 function.getName().toUpperCase() 是否在黑名单中
}
```

**对现有 `SqlExecutor.java` 的改动**：替换 `validateSql()` 方法体（约 +30 行），新增 `pom.xml` 依赖。  
**工作量：小（2-4小时）**

---

## 二、SQL 执行审计日志

### 2.1 审计日志表设计（参考 Apache Superset query 表）

Superset `query` 表核心字段：`database_id`、`user_id`、`sql`、`status`、`rows`、`error_message`、`start_time`、`end_time`、`limit_used`。

**本项目最小有用字段集**（新增 `user_question` 满足 NL2SQL 场景）：

```sql
CREATE TABLE sql_audit_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id INT          NOT NULL            COMMENT '数据源ID',
    user_question VARCHAR(1000)                    COMMENT '用户自然语言问题',
    sql_text      MEDIUMTEXT   NOT NULL            COMMENT '执行的 SQL',
    status        VARCHAR(20)  NOT NULL            COMMENT 'SUCCESS|FAILED|TIMEOUT',
    row_count     INT                              COMMENT '返回行数',
    truncated     TINYINT(1)   DEFAULT 0           COMMENT '是否被截断（200行上限）',
    latency_ms    BIGINT                           COMMENT '执行耗时（毫秒）',
    error_code    VARCHAR(50)                      COMMENT '错误码',
    error_message TEXT                             COMMENT '错误信息',
    execute_time  DATETIME     NOT NULL            COMMENT '执行时间',
    creator_id    BIGINT                           COMMENT '执行用户ID（待接入认证后填充）',
    INDEX idx_datasource_time (datasource_id, execute_time DESC),
    INDEX idx_creator_id      (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL执行审计日志，append-only';
```

### 2.2 AOP 拦截方案（推荐）

**推荐 AOP 拦截 `SqlExecutor.execute()`，理由**：审计是横切关注点，AOP 侵入性为零，`SqlExecutor` 保持职责单一，审计开关可独立控制。

```xml
<!-- pom.xml 新增 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

```java
@Aspect @Component
public class SqlAuditAspect {

    private final ApplicationEventPublisher eventPublisher;

    @Around("execution(* io.github.malonetalk.agent.datasource.SqlExecutor.execute(..))")
    public Object auditExecute(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        Datasource datasource = (Datasource) args[0];
        String sql = (String) args[1];
        long start = System.currentTimeMillis();

        SqlAuditLog log = new SqlAuditLog();
        log.setDatasourceId(datasource.getId());
        log.setSqlText(sql);
        log.setUserQuestion(AuditContext.getQuestion()); // 从上下文读用户问题
        log.setExecuteTime(LocalDateTime.now());

        try {
            Object result = pjp.proceed();
            QueryResult qr = (QueryResult) result;
            log.setStatus("SUCCESS");
            log.setRowCount(qr.getRows() != null ? qr.getRows().size() : 0);
            log.setTruncated(qr.isTruncated());
            log.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(e.getMessage());
            log.setLatencyMs(System.currentTimeMillis() - start);
            throw e;
        } finally {
            // 异步发布，不阻塞主流程
            eventPublisher.publishEvent(new AuditLogEvent(log));
        }
    }
}
```

### 2.3 用户问题传递：AuditContext

AOP 无法从 `SqlExecutor.execute()` 参数中拿到用户问题，通过 `ThreadLocal` 传递（此处 ThreadLocal 安全：`AgentService.chat()` 同步方法在同一 Servlet 线程设置，整个工具调用链路在同一线程完成）：

```java
public final class AuditContext {
    private static final ThreadLocal<String> USER_QUESTION = new ThreadLocal<>();
    public static void setQuestion(String q) { USER_QUESTION.set(q); }
    public static String getQuestion()        { return USER_QUESTION.get(); }
    public static void clear()                { USER_QUESTION.remove(); }
}
```

`AgentService.chat()` 中设置：
```java
AuditContext.setQuestion(userInput);
try {
    // ... 调用 agent.call()
} finally {
    AuditContext.clear();
}
```

**注意**：在流式接口 `chatStream()` 中，因为工具在 Reactor 线程执行，`AuditContext.getQuestion()` 可能为 null。流式路径的审计可以先记录空问题，后续通过 sessionId 关联补全，或接受此限制。

### 2.4 异步写入

**推荐 `@Async` + 独立小线程池，可接受丢失少量日志（NL2SQL 场景不是计费系统）：**

```java
@Configuration @EnableAsync
public class AsyncConfig {
    @Bean("auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2); e.setMaxPoolSize(4); e.setQueueCapacity(500);
        e.setThreadNamePrefix("audit-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        e.initialize();
        return e;
    }
}

@Service
public class SqlAuditLogService {
    @Async("auditExecutor")
    @EventListener
    public void onAuditLog(AuditLogEvent event) {
        auditLogMapper.insert(event.getLog());
    }
}
```

**新增文件**：`SqlAuditAspect.java`、`SqlAuditLog.java`、`SqlAuditLogMapper.java/.xml`、`AuditContext.java`、`AuditLogEvent.java`、`SqlAuditLogService.java`  
**`AgentService.java` 改动**：`chat()` 方法增加 2 行（setQuestion + clear）  
**工作量：中（1-2天，含建表和 Mapper）**

---

## 三、数据源连通性测试与健康检查

### 3.1 三种连通性验证方式比较

| 方式 | 原理 | 适用场景 | 结论 |
|---|---|---|---|
| `Connection.isValid(timeout)` | JDBC 4 标准，MySQL Connector/J 内部发送 `SELECT 1` | 已有连接池的健康检查 | 适合存量连接检查 |
| 手动 `SELECT 1` | 等同于 isValid，但可捕获更详细异常 | 调试时 | 无额外优势 |
| HikariCP `connectionTestQuery` | 池借用连接时的验证 | 池内连接验证 | 不适合"新建数据源时一次性验证" |

**推荐**：新建数据源验证用**临时直连（不入连接池）+ `isValid(5)`**，避免污染连接池，且能准确区分三种失败场景。

### 3.2 DynamicDataSourceManager 新增 testConnection()

```java
public ConnectionTestResult testConnection(Datasource datasource) {
    DataSourceType type = DataSourceType.fromCode(datasource.getType())
        .orElseThrow(() -> new IllegalArgumentException("Unsupported: " + datasource.getType()));
    String jdbcUrl = buildJdbcUrl(datasource, type); // 复用已有 JDBC URL 构建逻辑
    long start = System.currentTimeMillis();
    try {
        Class.forName(type.getDriverClassName());
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl, datasource.getUsername(), datasource.getPassword())) {
            boolean valid = conn.isValid(5);
            return valid
                ? ConnectionTestResult.success(System.currentTimeMillis() - start)
                : ConnectionTestResult.failure("CONNECTION_INVALID", "isValid returned false",
                    System.currentTimeMillis() - start);
        }
    } catch (ClassNotFoundException e) {
        return ConnectionTestResult.failure("DRIVER_NOT_FOUND", e.getMessage(), -1);
    } catch (SQLException e) {
        return ConnectionTestResult.failure(
            classifySqlError(e), e.getMessage(),
            System.currentTimeMillis() - start);
    }
}

private String classifySqlError(SQLException e) {
    int code = e.getErrorCode();
    String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
    if (code == 1045) return "AUTH_FAILED";           // 密码错误/用户不存在
    if (code == 1049) return "DATABASE_NOT_FOUND";    // 数据库名不存在
    if (msg.contains("communications link failure")
        || msg.contains("connect timed out"))  return "NETWORK_UNREACHABLE";
    if (msg.contains("unknown host"))          return "HOST_UNKNOWN";
    return "UNKNOWN_" + code;
}
```

```java
// ConnectionTestResult.java
public record ConnectionTestResult(
    boolean success, long latencyMs, String errorType, String errorMessage) {
    public static ConnectionTestResult success(long latency) {
        return new ConnectionTestResult(true, latency, null, null);
    }
    public static ConnectionTestResult failure(String errorType, String msg, long latency) {
        return new ConnectionTestResult(false, latency, errorType, msg);
    }
}
```

### 3.3 POST /api/datasource/{id}/test 接口

**DatasourceController.java 追加一个方法：**
```java
@PostMapping("/{id}/test")
public Result<ConnectionTestResult> testConnection(@PathVariable Integer id) {
    Datasource datasource = dataSourceService.findById(id);
    if (datasource == null) {
        return Result.error(404, "DataSource not found");
    }
    return Result.success(dataSourceService.testConnection(datasource));
}
```

**DatasourceService 接口新增：**
```java
ConnectionTestResult testConnection(Datasource datasource);
```

**DatasourceServiceImpl 实现**（同时回写 `test_status`）：
```java
@Override
public ConnectionTestResult testConnection(Datasource datasource) {
    ConnectionTestResult result = dynamicDataSourceManager.testConnection(datasource);
    // 回写测试状态
    Datasource update = new Datasource();
    update.setId(datasource.getId());
    update.setTestStatus(result.success() ? "SUCCESS" : "FAILED");
    update.setUpdateTime(LocalDateTime.now());
    dataSourceMapper.update(update);
    return result;
}
```

### 3.4 HikariCP keepaliveTime 配置（防 MySQL wait_timeout 断开）

在 `DynamicDataSourceManager.getHikariConfig()` 中补充：

```java
config.setKeepaliveTime(60_000L);           // 每60秒对空闲连接发 keepalive（默认0=禁用）
config.setConnectionInitSql("SELECT 1");    // 新建连接时验证
// connectionTestQuery 在 MySQL Connector/J 8+ 中不需要设置（驱动实现了 isValid()）
```

**对现有 `DynamicDataSourceManager.java` 的改动**：3 行配置 + `testConnection()` 方法（约 40 行）  
**工作量：小（2-4小时）**

---

## 四、密码安全治理

### 4.1 方案选择

| 方案 | 适用场景 | 复杂度 |
|---|---|---|
| Jasypt Spring Boot | 静态配置文件（`application.properties`）加密 | 小 |
| 自定义 AES-256/GCM | 动态数据源密码（存 DB 表）运行时加解密 | 中 |

**推荐组合**：Jasypt 处理 `application.properties` 中的管理库密码，自定义 AES-256/GCM 处理存在 `datasource` 表中的业务库密码。

### 4.2 Jasypt 集成（管理库密码保护）

```xml
<dependency>
    <groupId>com.github.ulisesbocchio</groupId>
    <artifactId>jasypt-spring-boot-starter</artifactId>
    <version>3.0.5</version>
</dependency>
```

```properties
# application.properties
jasypt.encryptor.password=${JASYPT_ENCRYPTOR_PASSWORD}
spring.datasource.password=ENC(加密后的密文)
```

Jasypt 同时支持**运行时手动调用解密**（适用于动态数据源场景）：
```java
@Autowired StringEncryptor encryptor;

private String decryptIfNeeded(String password) {
    if (password != null && password.startsWith("ENC(")) {
        return encryptor.decrypt(password.substring(4, password.length() - 1));
    }
    return password; // 兼容明文（迁移期）
}
```

### 4.3 AES-256/GCM 方案（不引入 Jasypt 时）

**推荐 AES-256/GCM 模式，纯 Java 标准库，认证加密防止密文篡改：**

```java
public final class AesGcmEncryptor {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12, TAG_LEN = 128;
    private final SecretKey key;

    public AesGcmEncryptor(String base64Key) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
    }

    public String encrypt(String plain) throws Exception {
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance(ALGORITHM);
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN, iv));
        byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[IV_LEN + enc.length];
        System.arraycopy(iv, 0, out, 0, IV_LEN);
        System.arraycopy(enc, 0, out, IV_LEN, enc.length);
        return Base64.getEncoder().encodeToString(out);
    }

    public String decrypt(String encBase64) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encBase64);
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_LEN);
        byte[] ct = Arrays.copyOfRange(combined, IV_LEN, combined.length);
        Cipher c = Cipher.getInstance(ALGORITHM);
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN, iv));
        return new String(c.doFinal(ct), StandardCharsets.UTF_8);
    }
}
```

密钥生成（一次性，存环境变量 `AES_MASTER_KEY`）：
```java
byte[] key = new byte[32]; // 256 bits
new SecureRandom().nextBytes(key);
System.out.println(Base64.getEncoder().encodeToString(key));
```

**解密发生在 `DynamicDataSourceManager.createDataSource()` 中**（最接近使用处，Service 层只做业务编排）：
```java
private HikariDataSource createDataSource(Datasource datasource) {
    // ...
    config.setPassword(decryptIfNeeded(datasource.getPassword()));
    // ...
}
```

### 4.4 API 响应密码脱敏

**推荐 Jackson 自定义 `@JsonSerialize`，零侵入 Service 层：**

```java
public class PasswordMaskSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider sp)
            throws IOException {
        gen.writeString("********"); // 固定掩码，不暴露长度
    }
}
```

```java
// Datasource.java 的 getter 上加注解
@JsonSerialize(using = PasswordMaskSerializer.class)
public String getPassword() { return password; }
```

**工作量：小（1小时，仅 API 脱敏）；中（1天，含 AES 加密存储）**

---

## 五、改动范围与优先级汇总

| 任务 | 涉及现有文件 | 新增文件 | 工作量 | 优先级 |
|---|---|---|---|---|
| JSqlParser SQL 校验 | `SqlExecutor.java`（+30行）、`pom.xml`（+1依赖） | 无 | 小（2-4h） | P0 |
| 数据源连通性测试接口 | `DatasourceController.java`（+1方法）、`DatasourceService.java`（+1方法）、`DatasourceServiceImpl.java`（+实现）、`DynamicDataSourceManager.java`（+方法+3行配置） | `ConnectionTestResult.java` | 小（4-6h） | P0 |
| API 密码脱敏 | `Datasource.java`（+1注解） | `PasswordMaskSerializer.java` | 小（1h） | P0 |
| 审计日志 AOP | `AgentService.java`（+2行）、`pom.xml`（+AOP依赖） | `SqlAuditAspect.java`、`SqlAuditLog.java`、`SqlAuditLogMapper.java/.xml`、`AuditContext.java`、`AuditLogEvent.java`、`SqlAuditLogService.java` + DB 建表 | 中（1-2天） | P1 |
| 密码加密存储 | `DynamicDataSourceManager.java`（+decryptIfNeeded）、`pom.xml`（可选Jasypt） | `AesGcmEncryptor.java`（或Jasypt配置） | 中（1天） | P1 |

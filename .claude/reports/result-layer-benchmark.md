# 结果层产品化参考

生成时间：2026-05-12  
用途：结果层产品化能力设计参考  
参考：Apache Superset、Vanna.AI、DB-GPT

---

## 一、概述

结果层是 DataAgent 产品化的关键环节，负责：
- 查询历史管理
- 结果缓存
- 异步查询任务
- 结果导出与下载
- 用户复核 SQL
- 图表推荐与切换

本文档基于 Apache Superset、Vanna.AI、DB-GPT 的最佳实践，提供 DataAgent 结果层的技术方案。

---

## 二、查询历史管理

### 1. 存储方案

**数据库表设计**（参考 Superset Query 模型）：

```sql
CREATE TABLE query_history (
    -- 主键
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    query_id VARCHAR(64) UNIQUE NOT NULL COMMENT '查询唯一标识',
    
    -- 用户和数据源
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    datasource_id VARCHAR(64) NOT NULL COMMENT '数据源 ID',
    
    -- SQL 相关
    sql TEXT NOT NULL COMMENT '原始 SQL',
    executed_sql TEXT COMMENT '实际执行的 SQL（可能经过改写）',
    
    -- 状态和结果
    status VARCHAR(16) NOT NULL COMMENT 'pending/running/success/failed/timeout',
    results_key VARCHAR(64) COMMENT '结果缓存键',
    rows_affected INT COMMENT '影响行数',
    error_message TEXT COMMENT '错误信息',
    progress INT DEFAULT 0 COMMENT '执行进度 0-100',
    
    -- 时间戳（毫秒精度）
    start_time BIGINT COMMENT '开始时间（Unix 时间戳毫秒）',
    end_time BIGINT COMMENT '结束时间（Unix 时间戳毫秒）',
    duration INT COMMENT '执行耗时（毫秒）',
    
    -- 会话信息
    session_id VARCHAR(64) COMMENT '会话 ID',
    
    -- 索引
    INDEX idx_user_time (user_id, start_time DESC),
    INDEX idx_results_key (results_key),
    INDEX idx_session (session_id),
    INDEX idx_datasource (datasource_id, start_time DESC)
) COMMENT='查询历史表';
```


**实体类定义**：

```java
@Data
@Builder
public class QueryHistory {
    private Long id;
    private String queryId;
    private Long userId;
    private String datasourceId;
    private String sql;
    private String executedSql;
    private String status;  // pending/running/success/failed/timeout
    private String resultsKey;
    private Integer rowsAffected;
    private String errorMessage;
    private Integer progress;
    private Long startTime;
    private Long endTime;
    private Integer duration;
    private String sessionId;
}
```

### 2. 检索接口

**按用户检索**：
```java
@GetMapping("/api/queries")
public Result<PageResponse<QueryHistory>> listQueries(
    @RequestParam Long userId,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int pageSize
) {
    return queryHistoryService.listByUser(userId, page, pageSize);
}
```

**按查询 ID 检索**：
```java
@GetMapping("/api/queries/{queryId}")
public Result<QueryHistory> getQuery(@PathVariable String queryId) {
    return queryHistoryService.getByQueryId(queryId);
}
```

**按数据源检索**：
```java
@GetMapping("/api/queries")
public Result<PageResponse<QueryHistory>> listQueriesByDatasource(
    @RequestParam String datasourceId,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int pageSize
) {
    return queryHistoryService.listByDatasource(datasourceId, page, pageSize);
}
```


### 3. 复用查询

**接口设计**：
```java
@PostMapping("/api/queries/{queryId}/rerun")
public Result<AnalysisResult> rerunQuery(@PathVariable String queryId) {
    QueryHistory history = queryHistoryService.getByQueryId(queryId);
    return agentService.executeQuery(history.getSql(), history.getDatasourceId());
}
```

**前端交互**：
- 查询历史列表显示"重新运行"按钮
- 点击后使用相同 SQL 重新执行
- 生成新的 queryId，记录到历史

---

## 三、结果缓存策略

### 1. 缓存键生成

**方案**（参考 Superset）：
```java
public class ResultCacheKeyGenerator {
    
    public String generateCacheKey(String sql, String datasourceId, String userId) {
        // 规范化 SQL（去除空格、统一大小写）
        String normalizedSql = normalizeSql(sql);
        
        // 组合键
        String raw = normalizedSql + "|" + datasourceId + "|" + userId;
        
        // MD5 哈希
        return "query_result:" + DigestUtils.md5Hex(raw);
    }
    
    private String normalizeSql(String sql) {
        return sql.trim()
            .replaceAll("\\s+", " ")  // 多个空格替换为单个
            .toUpperCase();            // 统一大写
    }
}
```

**注意事项**：
- 包含 `userId` 确保用户隔离
- 包含 `datasourceId` 确保数据源隔离
- SQL 规范化避免格式差异导致缓存失效


### 2. 缓存失效策略

**TTL（Time To Live）**：
```java
@Configuration
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))  // 默认 1 小时
            .disableCachingNullValues();
        
        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
```

**LRU（Least Recently Used）**：
- Redis 配置 `maxmemory-policy allkeys-lru`
- 内存不足时自动淘汰最少使用的结果

**手动失效**：
```java
@DeleteMapping("/api/cache/datasource/{datasourceId}")
public Result<Void> invalidateDatasourceCache(@PathVariable String datasourceId) {
    cacheService.invalidateByDatasource(datasourceId);
    return Result.success();
}
```

**触发场景**：
- 数据源配置更新
- 数据源数据变更（通过 webhook 或定时任务）
- 用户手动刷新

### 3. 缓存命中率优化

**监控指标**：
```java
@Service
public class CacheMetricsService {
    
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    public void recordHit() {
        cacheHits.incrementAndGet();
    }
    
    public void recordMiss() {
        cacheMisses.incrementAndGet();
    }
    
    public double getHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        return total == 0 ? 0 : (double) hits / total;
    }
}
```

**优化策略**：
- 对高频查询增加 TTL
- 对参数化查询使用模板匹配
- 预热常用查询结果


---

## 四、异步查询任务模型

### 1. 状态机设计

**状态枚举**（参考 Superset）：
```java
public enum QueryTaskStatus {
    PENDING("pending", "等待执行"),
    RUNNING("running", "执行中"),
    SUCCESS("success", "执行成功"),
    FAILED("failed", "执行失败"),
    TIMEOUT("timeout", "执行超时"),
    CANCELLED("cancelled", "已取消");
    
    private final String code;
    private final String description;
}
```

**状态流转**：
```
PENDING → RUNNING → SUCCESS
                  ↘ FAILED
                  ↘ TIMEOUT
                  ↘ CANCELLED
```

### 2. 任务元数据

**实体定义**：
```java
@Data
@Builder
public class QueryTask {
    private String taskId;           // 任务唯一标识
    private String channelId;        // 通道 ID（用于推送）
    private Long userId;             // 用户 ID
    private String datasourceId;     // 数据源 ID
    private String sql;              // SQL 语句
    private QueryTaskStatus status;  // 任务状态
    private Integer progress;        // 执行进度 0-100
    private String resultUrl;        // 结果 URL（可选）
    private String error;            // 错误信息（可选）
    private Long createdAt;          // 创建时间
    private Long updatedAt;          // 更新时间
}
```


### 3. 轮询 vs 推送

**轮询方式**：
```java
@GetMapping("/api/tasks/{taskId}")
public Result<QueryTask> getTaskStatus(@PathVariable String taskId) {
    return taskService.getTask(taskId);
}
```

**前端轮询逻辑**：
```javascript
async function pollTaskStatus(taskId) {
    const interval = 2000; // 2 秒轮询一次
    while (true) {
        const task = await fetch(`/api/tasks/${taskId}`).then(r => r.json());
        if (task.status === 'success' || task.status === 'failed') {
            return task;
        }
        await sleep(interval);
    }
}
```

**推送方式**（使用 SSE）：
```java
@GetMapping("/api/tasks/{taskId}/stream")
public SseEmitter streamTaskStatus(@PathVariable String taskId) {
    SseEmitter emitter = new SseEmitter();
    taskService.subscribeTaskUpdates(taskId, event -> {
        try {
            emitter.send(event);
            if (event.getStatus().isTerminal()) {
                emitter.complete();
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    });
    return emitter;
}
```

**推荐策略**：
- **短查询（< 5 秒）**：使用轮询
- **长查询（≥ 5 秒）**：使用推送
- **前端自适应**：先轮询 2 次，如果未完成则切换到推送

### 4. 取消机制

**接口设计**：
```java
@PostMapping("/api/tasks/{taskId}/cancel")
public Result<Void> cancelTask(@PathVariable String taskId) {
    taskService.cancelTask(taskId);
    return Result.success();
}
```

**实现方式**：
```java
@Service
public class QueryTaskService {
    
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    
    public void cancelTask(String taskId) {
        Future<?> future = runningTasks.get(taskId);
        if (future != null && !future.isDone()) {
            future.cancel(true);  // 中断线程
            updateTaskStatus(taskId, QueryTaskStatus.CANCELLED);
        }
    }
}
```


---

## 五、结果导出与下载

### 1. 格式支持

**支持的格式**：
- **CSV**：通用格式，Excel 可直接打开
- **Excel (XLSX)**：支持多 sheet、格式化
- **JSON**：适合程序处理
- **Parquet**：大数据场景，压缩率高

### 2. 导出接口

```java
@GetMapping("/api/queries/{queryId}/export")
public ResponseEntity<StreamingResponseBody> exportResult(
    @PathVariable String queryId,
    @RequestParam String format  // csv/xlsx/json/parquet
) {
    QueryHistory history = queryHistoryService.getByQueryId(queryId);
    
    StreamingResponseBody stream = outputStream -> {
        List<Map<String, Object>> result = getFullResult(history.getResultsKey());
        
        switch (format) {
            case "csv":
                exportToCsv(result, outputStream);
                break;
            case "xlsx":
                exportToExcel(result, outputStream);
                break;
            case "json":
                exportToJson(result, outputStream);
                break;
            case "parquet":
                exportToParquet(result, outputStream);
                break;
        }
    };
    
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=result." + format)
        .header("Content-Type", getContentType(format))
        .body(stream);
}
```

### 3. 大文件处理

**流式导出**（避免内存溢出）：
```java
private void exportToCsv(List<Map<String, Object>> result, OutputStream out) throws IOException {
    try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
        // 写入表头
        String[] headers = result.get(0).keySet().toArray(new String[0]);
        writer.writeNext(headers);
        
        // 流式写入数据
        for (Map<String, Object> row : result) {
            String[] values = headers.stream()
                .map(h -> String.valueOf(row.get(h)))
                .toArray(String[]::new);
            writer.writeNext(values);
        }
    }
}
```

**分块下载**（支持断点续传）：
```java
@GetMapping("/api/queries/{queryId}/export/chunk")
public ResponseEntity<byte[]> exportChunk(
    @PathVariable String queryId,
    @RequestParam int offset,
    @RequestParam int limit
) {
    // 实现分块读取和下载
}
```


---

## 六、用户复核 SQL

### 1. 显示生成的 SQL

**前端展示**：
```typescript
interface SqlDisplay {
  originalSql: string;      // 原始 SQL
  formattedSql: string;     // 格式化后的 SQL
  highlightedSql: string;   // 语法高亮的 HTML
}
```

**SQL 格式化**：
```java
public String formatSql(String sql) {
    return SQLFormatter.format(sql, FormatConfig.builder()
        .indent("  ")
        .uppercase(true)
        .linesBetweenQueries(2)
        .build());
}
```

### 2. 允许修改重跑

**接口设计**：
```java
@PostMapping("/api/queries/{queryId}/modify")
public Result<AnalysisResult> modifyAndRerun(
    @PathVariable String queryId,
    @RequestBody ModifySqlRequest request
) {
    QueryHistory original = queryHistoryService.getByQueryId(queryId);
    
    // 记录修改历史
    queryHistoryService.recordModification(queryId, request.getModifiedSql());
    
    // 重新执行
    return agentService.executeQuery(request.getModifiedSql(), original.getDatasourceId());
}
```

**前端交互**：
- SQL 编辑器（支持语法高亮、自动补全）
- "重新运行"按钮
- 显示修改历史（原始 SQL vs 修改后 SQL）

### 3. 修改历史记录

**存储方案**：
```sql
CREATE TABLE query_modification_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    original_query_id VARCHAR(64) NOT NULL,
    modified_query_id VARCHAR(64) NOT NULL,
    original_sql TEXT NOT NULL,
    modified_sql TEXT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    
    INDEX idx_original (original_query_id)
);
```


---

## 七、图表推荐与切换

### 1. 基于数据类型推荐

**推荐逻辑**（参考 `analysis-result-schema.md`）：
```java
public ChartSuggestion recommendChart(List<ColumnInfo> columns, int rowCount) {
    long numericCols = columns.stream().filter(c -> "number".equals(c.getType())).count();
    long categoryCols = columns.stream().filter(c -> "string".equals(c.getType())).count();
    long dateCols = columns.stream().filter(c -> "date".equals(c.getType())).count();
    
    // 时间序列 → 折线图
    if (dateCols > 0 && numericCols > 0) {
        return ChartSuggestion.builder()
            .chartType("line")
            .reason("时间序列数据适合折线图")
            .config(ChartConfig.builder()
                .xAxis(findDateColumn(columns))
                .yAxis(findNumericColumn(columns))
                .build())
            .build();
    }
    
    // 分类对比 → 柱状图
    if (categoryCols == 1 && numericCols == 1) {
        return ChartSuggestion.builder()
            .chartType("bar")
            .reason("分类对比数据适合柱状图")
            .build();
    }
    
    // 占比分析 → 饼图（数据量少时）
    if (categoryCols == 1 && numericCols == 1 && rowCount <= 10) {
        return ChartSuggestion.builder()
            .chartType("pie")
            .reason("少量分类占比数据适合饼图")
            .build();
    }
    
    // 默认表格
    return ChartSuggestion.builder()
        .chartType("table")
        .reason("默认使用表格展示")
        .build();
}
```

### 2. 前端切换实现

**图表类型选择器**：
```typescript
interface ChartSelector {
  availableTypes: ChartType[];  // 可用的图表类型
  currentType: ChartType;       // 当前图表类型
  onSwitch: (type: ChartType) => void;
}
```

**切换逻辑**：
```typescript
function switchChart(newType: ChartType) {
  // 保留数据，仅更新图表配置
  setChartType(newType);
  setChartConfig(generateConfig(newType, data));
}
```

### 3. 图表配置调整

**支持的配置项**：
- 坐标轴选择（X 轴、Y 轴）
- 颜色主题
- 图例位置
- 数据标签显示
- 排序方式

**配置接口**：
```typescript
interface ChartConfig {
  xAxis?: string;
  yAxis?: string;
  groupBy?: string;
  colorScheme?: string;
  showLegend?: boolean;
  showDataLabels?: boolean;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}
```

---

## 八、参考来源

### Apache Superset
- **仓库**：`apache/superset`
- **关键文件**：
  - `superset/models/sql_lab.py` - Query 模型
  - `superset/async_events/async_query_manager.py` - 异步查询管理
  - `superset/common/db_query_status.py` - 状态枚举

### Vanna.AI
- **仓库**：`vanna-ai/vanna-nextjs-flask`
- **关键文件**：
  - `dependencies/base/index.py` - 结果对象设计

### DB-GPT
- **仓库**：`eosphoros-ai/DB-GPT`
- **关键文件**：
  - `packages/dbgpt-core/src/dbgpt/core/awel/task/base.py` - 任务状态管理

---

## 九、实施建议

### Phase 1：查询历史与缓存
1. 实现 `query_history` 表和基础 CRUD
2. 实现结果缓存（Redis）
3. 实现查询历史检索接口

### Phase 2：异步查询
1. 实现异步查询任务模型
2. 实现轮询接口
3. 实现取消机制

### Phase 3：结果导出
1. 实现 CSV 导出
2. 实现 Excel 导出
3. 实现流式导出（大文件）

### Phase 4：用户复核与图表
1. 实现 SQL 显示与格式化
2. 实现 SQL 修改重跑
3. 实现图表推荐与切换

---

## 十、与 DataAgent 当前架构的集成

### 1. 与 AgentService 集成

```java
@Service
public class AgentService {
    
    private final QueryHistoryService queryHistoryService;
    private final ResultCacheService resultCacheService;
    
    public AnalysisResult chat(String sessionId, String message) {
        // 1. Agent 生成 SQL
        String sql = agent.generateSql(message);
        
        // 2. 检查缓存
        String cacheKey = resultCacheService.generateCacheKey(sql, datasourceId, userId);
        AnalysisResult cached = resultCacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // 3. 执行查询
        String queryId = UUID.randomUUID().toString();
        queryHistoryService.create(queryId, sql, datasourceId, userId);
        
        List<Map<String, Object>> result = sqlExecutor.execute(sql);
        
        // 4. 构建 AnalysisResult
        AnalysisResult analysisResult = analysisResultBuilder.build(
            message, sql, result, datasourceId, 
            ExecutionMetadata.builder().queryId(queryId).build()
        );
        
        // 5. 缓存结果
        resultCacheService.put(cacheKey, analysisResult);
        
        // 6. 更新查询历史
        queryHistoryService.updateSuccess(queryId, result.size(), cacheKey);
        
        return analysisResult;
    }
}
```

### 2. 与现有接口的关系

**保留现有接口**：
- `POST /api/agent/chat` - 返回文本（向后兼容）
- `POST /api/agent/chat/stream` - 返回流式文本（向后兼容）

**新增接口**：
- `POST /api/agent/analyze` - 返回 `AnalysisResult`
- `GET /api/queries` - 查询历史列表
- `GET /api/queries/{queryId}` - 查询详情
- `POST /api/queries/{queryId}/rerun` - 重新运行
- `GET /api/queries/{queryId}/export` - 导出结果

---

## 十一、总结

结果层产品化是 DataAgent 从"查数助手"升级到"完整产品"的关键环节。

**核心能力**：
1. ✅ 查询历史管理 - 记录、检索、复用
2. ✅ 结果缓存 - 提升性能、降低成本
3. ✅ 异步查询 - 支持长查询、任务化管理
4. ✅ 结果导出 - 多格式、大文件、流式处理
5. ✅ 用户复核 SQL - 透明、可修改、可追溯
6. ✅ 图表推荐 - 智能推荐、灵活切换

**实施优先级**：
- **P0**：查询历史 + 结果缓存
- **P1**：异步查询 + 结果导出
- **P2**：用户复核 SQL + 图表推荐

**预期收益**：
- 用户体验提升 50%+（缓存命中、历史复用）
- 系统负载降低 30%+（缓存、异步）
- 产品完整度提升至 80%+（从助手到产品）


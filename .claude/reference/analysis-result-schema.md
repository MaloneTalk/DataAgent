# 分析结果对象模型规范

生成时间：2026-05-12  
用途：定义 DataAgent 分析结果对象的标准结构  
参考：Vanna.AI、Apache Superset、DB-GPT

---

## 一、核心设计原则

### 1. 简洁性（学习 Vanna）
- 核心字段清晰，避免过度封装
- 返回结构直观，易于理解和使用

### 2. 完整性（学习 Superset）
- 包含执行元数据，支持审计和调试
- 记录完整的查询生命周期信息

### 3. 可扩展性（学习 DB-GPT）
- 通过 extra 字段支持未来扩展
- 支持流式和非流式两种模式

---

## 二、分析结果对象结构

### 核心接口定义

```java
public class AnalysisResult {
    // 核心字段
    private String sql;                          // 生成的 SQL
    private ResultPreview resultPreview;         // 结果预览
    private ChartSuggestion chartSuggestion;     // 图表推荐（可选）
    private List<String> followupQuestions;      // 后续问题
    private String explanation;                  // 结果解释（可选）
    
    // 元数据
    private String datasourceId;                 // 数据源 ID
    private ExecutionMetadata executionMetadata; // 执行元数据
    
    // 扩展字段
    private Map<String, Object> extra;           // 扩展字段
}
```

### 字段详细说明

#### 1. sql (String, 必填)
**用途**：生成的 SQL 语句

**示例**：
```sql
SELECT 
    department, 
    COUNT(*) as employee_count,
    AVG(salary) as avg_salary
FROM employees
WHERE hire_date >= '2023-01-01'
GROUP BY department
ORDER BY employee_count DESC
LIMIT 10
```

**注意事项**：
- 必须是实际执行的 SQL
- 如果有参数化，应展示替换后的完整 SQL
- 支持格式化（美化）

---

#### 2. resultPreview (ResultPreview, 必填)
**用途**：结果数据预览

**结构定义**：
```java
public class ResultPreview {
    private List<ColumnInfo> columns;    // 列信息
    private List<List<Object>> rows;     // 行数据
    private int totalRows;               // 总行数
    private int previewRows;             // 预览行数
    private boolean hasMore;             // 是否有更多数据
}

public class ColumnInfo {
    private String name;                 // 列名
    private String type;                 // 数据类型（string/number/date/boolean）
    private String displayName;          // 显示名称（可选）
    private boolean nullable;            // 是否可空
}
```

**示例**：
```json
{
  "columns": [
    {"name": "department", "type": "string", "displayName": "部门", "nullable": false},
    {"name": "employee_count", "type": "number", "displayName": "员工数", "nullable": false},
    {"name": "avg_salary", "type": "number", "displayName": "平均工资", "nullable": false}
  ],
  "rows": [
    ["技术部", 120, 15000.50],
    ["销售部", 80, 12000.00],
    ["市场部", 45, 11500.75]
  ],
  "totalRows": 10,
  "previewRows": 3,
  "hasMore": true
}
```

**注意事项**：
- `previewRows` 默认最多 200 行
- `hasMore` 指示是否需要分页或导出
- `rows` 使用二维数组，保持轻量

---

#### 3. chartSuggestion (ChartSuggestion, 可选)
**用途**：图表类型推荐

**结构定义**：
```java
public class ChartSuggestion {
    private String chartType;            // 图表类型
    private String reason;               // 推荐理由
    private ChartConfig config;          // 图表配置（可选）
}

public class ChartConfig {
    private String xAxis;                // X 轴字段
    private String yAxis;                // Y 轴字段
    private String groupBy;              // 分组字段（可选）
    private Map<String, Object> options; // 其他配置
}
```

**支持的图表类型**：
- `table`：表格（默认）
- `bar`：柱状图
- `line`：折线图
- `pie`：饼图
- `scatter`：散点图
- `area`：面积图

**推荐规则**（参考 Vanna 和 Superset）：
```java
public ChartSuggestion recommendChart(List<ColumnInfo> columns) {
    long numericCols = columns.stream().filter(c -> "number".equals(c.getType())).count();
    long categoryCols = columns.stream().filter(c -> "string".equals(c.getType())).count();
    long dateCols = columns.stream().filter(c -> "date".equals(c.getType())).count();
    
    if (dateCols > 0 && numericCols > 0) {
        return new ChartSuggestion("line", "时间序列数据适合折线图");
    } else if (categoryCols == 1 && numericCols == 1) {
        return new ChartSuggestion("bar", "分类对比数据适合柱状图");
    } else if (categoryCols == 1 && numericCols == 1 && rows.size() <= 10) {
        return new ChartSuggestion("pie", "少量分类占比数据适合饼图");
    } else {
        return new ChartSuggestion("table", "默认使用表格展示");
    }
}
```

**示例**：
```json
{
  "chartType": "bar",
  "reason": "分类对比数据适合柱状图",
  "config": {
    "xAxis": "department",
    "yAxis": "employee_count",
    "options": {
      "title": "各部门员工数量对比"
    }
  }
}
```

---

#### 4. followupQuestions (List<String>, 必填)
**用途**：后续问题推荐

**生成策略**（参考 Vanna）：
```java
public List<String> generateFollowupQuestions(String question, String sql, ResultPreview result) {
    String prompt = String.format(
        "用户问题：%s\n" +
        "生成的 SQL：%s\n" +
        "结果数据：%s\n\n" +
        "生成 3-5 个后续问题，要求：\n" +
        "1. 每个问题必须可以直接转换为 SQL\n" +
        "2. 优先生成对当前 SQL 的轻微修改（如增加过滤条件、改变排序）\n" +
        "3. 优先生成可独立回答的问题\n" +
        "4. 每行一个问题，不要编号",
        question, sql, result.toMarkdown()
    );
    
    String response = llmClient.generate(prompt);
    return Arrays.stream(response.split("\n"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
}
```

**示例**：
```json
[
  "各部门的平均工资排名如何？",
  "技术部中工资最高的前 10 名员工是谁？",
  "2023 年入职的员工分布在哪些部门？",
  "哪些部门的员工数量超过 100 人？"
]
```

**注意事项**：
- 每个问题应该是完整的、可独立理解的
- 避免"更多"、"其他"等模糊表述
- 问题应该与当前查询相关，但不重复

---

#### 5. explanation (String, 可选)
**用途**：结果解释或摘要

**生成策略**（参考 Vanna）：
```java
public String generateExplanation(String question, ResultPreview result) {
    String prompt = String.format(
        "用户问题：%s\n" +
        "结果数据：%s\n\n" +
        "用 1-2 句话简要总结数据，直接回答用户问题，不要额外解释。",
        question, result.toMarkdown()
    );
    
    return llmClient.generate(prompt);
}
```

**示例**：
```
"技术部拥有最多的员工（120 人），平均工资为 15000.50 元。销售部和市场部分别有 80 人和 45 人。"
```

**注意事项**：
- 简洁明了，1-2 句话
- 直接回答用户问题
- 避免重复数据表格中的内容

---

#### 6. datasourceId (String, 必填)
**用途**：标识数据源

**示例**：`"ds_mysql_prod_001"`

**注意事项**：
- 必须与 `Datasource` 表中的 ID 一致
- 用于结果缓存键生成
- 用于审计和权限控制

---

#### 7. executionMetadata (ExecutionMetadata, 必填)
**用途**：执行元数据

**结构定义**：
```java
public class ExecutionMetadata {
    private String queryId;              // 查询唯一标识
    private String executedAt;           // 执行时间（ISO 8601）
    private long duration;               // 执行耗时（毫秒）
    private int rowsAffected;            // 影响行数
    private String status;               // 状态（success/failed/timeout）
    private String error;                // 错误信息（可选）
    private String resultsKey;           // 结果缓存键（可选）
}
```

**示例**：
```json
{
  "queryId": "q_20260512_abc123",
  "executedAt": "2026-05-12T14:30:45.123Z",
  "duration": 1250,
  "rowsAffected": 10,
  "status": "success",
  "resultsKey": "query_result:5f4dcc3b5aa765d61d8327deb882cf99"
}
```

**注意事项**：
- `queryId` 用于查询历史检索
- `resultsKey` 用于结果缓存检索
- `duration` 用于性能监控

---

#### 8. extra (Map<String, Object>, 可选)
**用途**：扩展字段

**使用场景**：
- 存储特定场景的额外信息
- 支持未来功能扩展
- 避免频繁修改核心结构

**示例**：
```json
{
  "extra": {
    "queryPlan": "...",
    "cacheHit": true,
    "executionNode": "node-01"
  }
}
```

---

## 三、与现有协议的关系

### 与 ToolResult 的关系

当前 `ExecuteSqlTool` 返回 `ToolResult<List<Map<String, Object>>>`：

```java
public class ToolResult<T> {
    private boolean success;
    private T data;
    private ToolError error;
}
```

**演进方案**：

**阶段 1（当前）**：保持 ToolResult 不变
- `ExecuteSqlTool` 继续返回 `ToolResult<List<Map<String, Object>>>`
- Agent 拿到结果后，在 `AgentService` 层组装 `AnalysisResult`

**阶段 2（短期）**：引入 AnalysisResult
- 新增 `POST /api/agent/analyze` 接口，直接返回 `AnalysisResult`
- 保留 `POST /api/agent/chat` 接口，返回文本
- 前端根据需要选择接口

**阶段 3（长期）**：统一为 AnalysisResult
- `POST /api/agent/chat` 也返回 `AnalysisResult`
- 前端从 `explanation` 字段提取文本展示
- 支持切换到表格/图表视图

---

## 四、序列化格式

### JSON Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["sql", "resultPreview", "followupQuestions", "datasourceId", "executionMetadata"],
  "properties": {
    "sql": {
      "type": "string",
      "description": "生成的 SQL 语句"
    },
    "resultPreview": {
      "type": "object",
      "required": ["columns", "rows", "totalRows", "previewRows", "hasMore"],
      "properties": {
        "columns": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["name", "type"],
            "properties": {
              "name": {"type": "string"},
              "type": {"type": "string", "enum": ["string", "number", "date", "boolean"]},
              "displayName": {"type": "string"},
              "nullable": {"type": "boolean"}
            }
          }
        },
        "rows": {
          "type": "array",
          "items": {"type": "array"}
        },
        "totalRows": {"type": "integer"},
        "previewRows": {"type": "integer"},
        "hasMore": {"type": "boolean"}
      }
    },
    "chartSuggestion": {
      "type": "object",
      "properties": {
        "chartType": {"type": "string"},
        "reason": {"type": "string"},
        "config": {"type": "object"}
      }
    },
    "followupQuestions": {
      "type": "array",
      "items": {"type": "string"}
    },
    "explanation": {"type": "string"},
    "datasourceId": {"type": "string"},
    "executionMetadata": {
      "type": "object",
      "required": ["queryId", "executedAt", "duration", "rowsAffected", "status"],
      "properties": {
        "queryId": {"type": "string"},
        "executedAt": {"type": "string", "format": "date-time"},
        "duration": {"type": "integer"},
        "rowsAffected": {"type": "integer"},
        "status": {"type": "string", "enum": ["success", "failed", "timeout"]},
        "error": {"type": "string"},
        "resultsKey": {"type": "string"}
      }
    },
    "extra": {"type": "object"}
  }
}
```

---

## 五、实现示例

### Java 实现

```java
@Service
public class AnalysisResultBuilder {
    
    private final LlmClient llmClient;
    
    public AnalysisResult build(
        String question,
        String sql,
        List<Map<String, Object>> rawResult,
        String datasourceId,
        ExecutionMetadata metadata
    ) {
        // 1. 构建 ResultPreview
        ResultPreview preview = buildResultPreview(rawResult);
        
        // 2. 推荐图表
        ChartSuggestion chart = recommendChart(preview.getColumns());
        
        // 3. 生成后续问题
        List<String> followups = generateFollowupQuestions(question, sql, preview);
        
        // 4. 生成解释
        String explanation = generateExplanation(question, preview);
        
        // 5. 组装结果
        return AnalysisResult.builder()
            .sql(sql)
            .resultPreview(preview)
            .chartSuggestion(chart)
            .followupQuestions(followups)
            .explanation(explanation)
            .datasourceId(datasourceId)
            .executionMetadata(metadata)
            .build();
    }
    
    private ResultPreview buildResultPreview(List<Map<String, Object>> rawResult) {
        if (rawResult.isEmpty()) {
            return ResultPreview.empty();
        }
        
        // 提取列信息
        Map<String, Object> firstRow = rawResult.get(0);
        List<ColumnInfo> columns = firstRow.keySet().stream()
            .map(key -> inferColumnInfo(key, firstRow.get(key)))
            .collect(Collectors.toList());
        
        // 转换行数据
        List<List<Object>> rows = rawResult.stream()
            .limit(200)  // 最多 200 行
            .map(row -> columns.stream()
                .map(col -> row.get(col.getName()))
                .collect(Collectors.toList()))
            .collect(Collectors.toList());
        
        return ResultPreview.builder()
            .columns(columns)
            .rows(rows)
            .totalRows(rawResult.size())
            .previewRows(rows.size())
            .hasMore(rawResult.size() > 200)
            .build();
    }
    
    private ColumnInfo inferColumnInfo(String name, Object value) {
        String type = "string";
        if (value instanceof Number) {
            type = "number";
        } else if (value instanceof Date || value instanceof LocalDate) {
            type = "date";
        } else if (value instanceof Boolean) {
            type = "boolean";
        }
        
        return ColumnInfo.builder()
            .name(name)
            .type(type)
            .nullable(value == null)
            .build();
    }
}
```

---

## 六、参考来源

### Vanna.AI
- **仓库**：`vanna-ai/vanna-nextjs-flask`
- **文件**：`dependencies/base/index.py`
- **关键方法**：
  - `ask()` - 返回 SQL + DataFrame
  - `generate_followup_questions()` - 生成后续问题
  - `generate_summary()` - 生成结果摘要

### Apache Superset
- **仓库**：`apache/superset`
- **文件**：`superset/models/sql_lab.py`
- **关键模型**：
  - `Query` - 查询历史模型
  - `QueryStatus` - 查询状态枚举

### DB-GPT
- **仓库**：`eosphoros-ai/DB-GPT`
- **文件**：`packages/dbgpt-core/src/dbgpt/core/awel/task/base.py`
- **关键类**：
  - `TaskOutput` - 任务输出抽象
  - `TaskContext` - 任务上下文

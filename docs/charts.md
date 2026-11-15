# 交互图表

聊天中的 `generate_chart` 工具可以将本会话 `execute_sql` 的真实查询结果绘制成图表。前端使用 ECharts 展示，支持柱状图、折线图、饼图和散点图。

## 使用方式

先接入并激活数据源、同步表或普通视图，再用自然语言说明统计口径和图表类型，例如：

- “统计上个月各区域销售额，用柱状图展示。”
- “按月汇总今年销售额和退款额，画成折线图。”
- “把刚才的区域销售额改成饼图。”

Agent 先在 SQL 中完成聚合、筛选与排序，再使用返回的 `Result ID` 指定字段绘图。修改图表类型时可以复用本会话的同一份结果；修改统计口径或获取最新数据时应重新执行 SQL。绘图读取已保存的查询快照，不会重新查询数据库。

普通用户需要 `analysis:chat:use`、`data:schema:read`、`analysis:sql:execute` 及目标表或视图的访问权限；当前没有单独的图表权限点。绘图前会重新检查数据源范围、SQL 权限和表列权限。详见 [权限说明](permissions.md)。

## 工具参数

以下是 Agent 调用 `generate_chart` 的参数示例；`result_id` 必须替换为本会话 `execute_sql` 返回的值，字段名必须与查询结果列名或别名完全一致：

```json
{
  "result_id": "<execute_sql 返回的 Result ID>",
  "chart_type": "bar",
  "title": "各区域销售额",
  "x_field": "region",
  "y_fields": ["sales"],
  "series_field": ""
}
```

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `result_id` | 是 | 本会话内的 SQL 结果引用，不能引用其他会话的结果 |
| `chart_type` | 是 | `bar`、`line`、`pie` 或 `scatter` |
| `title` | 是 | 图表标题 |
| `x_field` | 是 | 分类字段；散点图中为数值横轴 |
| `y_fields` | 是 | 非空且不重复的数值字段列表 |
| `series_field` | 否 | 分组字段；省略或空字符串表示不分组 |

工具只接受结果引用和字段映射，不接受模型手写的数据、JavaScript 代码或任意 ECharts 配置。

## 图表与数据限制

| 类型 | 数据要求 |
| --- | --- |
| 柱状图 / 折线图 | 可使用多个指标和可选分组；同一个横轴值与分组值的组合只能有一行。指标允许部分值为 NULL，但每个指标至少有一个有效数值 |
| 饼图 | 只允许一个指标、不允许分组；指标必须为非负数且至少一个值大于 0，不能有 NULL；分类值不能重复 |
| 散点图 | 横轴和各指标必须为有限数值，不能有 NULL；允许重复坐标 |

所有类型都要求横轴字段非 NULL；启用分组时分组字段也不能为 NULL。分组数量乘以指标数量最多为 20 条系列；无分组时按一个分组计算。

查询结果必须非空且未截断。`execute_sql` 最多返回 200 行；需要展示更多原始记录时，应先在 SQL 中按业务维度聚合或筛选。不要只截取部分记录来绘制声称覆盖全量数据的图表。

SQL 结果快照序列化后最多为 256 KiB。超限时 SQL 查询仍返回文本结果，但不保存绘图快照，并提示 `Chart snapshot exceeds size limit`；减少查询列或聚合后重查。旧会话中没有结果快照的 SQL 也需要重新执行。

## 流式传输与历史

成功绘图沿用 `tool_result` 事件：`toolResult.name` 为 `generate_chart`，`toolResult.chart` 包含 `spec` 和 `rows`，没有独立的 `chart` SSE 事件。`spec` 使用 `chartType`、`title`、`xField`、`yFields`、`seriesField` 等驼峰字段名。

图表随工具结果保存到会话，历史回放会重新渲染。图表展示在聊天消息中，当前没有独立的图表资产页，也未通过 `/agent/**` REST 接口暴露 `generate_chart`。外部接入能力见 [外部 Agent 接入](external-agent.md)。

## 常见问题

| 现象 | 处理方式 |
| --- | --- |
| 找不到 Result ID | 确认引用来自当前会话；旧结果无快照或快照超限时重新查询 |
| 结果被截断 | 缩小范围或在 SQL 中聚合后重新绘图 |
| 维度重复 | 在 SQL 中按 `x_field` 和可选的 `series_field` 聚合 |
| 指标不是数值 | 先在 SQL 中转换为数值；金额和百分号等展示文本不适合作为指标列 |
| 系列超过 20 条 | 减少指标或分组数量 |
| 改图时权限被拒绝 | 检查当前数据源状态、SQL 权限和表列授权；历史快照不会绕过当前权限 |

实现入口：后端 `GenerateChartTool`、`ChartService`、`ChartConverter`；前端 `ChatChart.vue` 与 `utils/chartOption.ts`。

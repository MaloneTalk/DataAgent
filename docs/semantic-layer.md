# 语义层

语义层是 Data Agent 把"业务语言"翻译成"数据表示"的核心桥梁。没有它，LLM 只能凭字段名瞎猜；有了它，LLM 才知道"销售额"对应哪张表、哪个字段、"上个月"用 `settle_time` 还是 `create_time`。

## 1. 为什么需要语义层

业务人员说的词（"流水""GMV""复购率"）和数据库里的字段（`paid_amount`、`status`）往往对不上。直接让 LLM 猜，会出现：

- 字段/表名猜错 → SQL 报错或查出空结果；
- 口径不一致 → 不同人问"销售额"得到不同数字；
- 时间维度错用 → 用下单时间而非结算时间，统计口径全错。

语义层把这些"业务 ↔ 数据"的映射显式沉淀下来，让 Agent 每次答题时按需取用，稳定且可审计。

## 2. 语义层的组成

管理操作的权限以各 Controller 的 `@RequirePermission` 标记为准。指标创建、更新和删除需要该权限，指标查询要求登录。前端入口是「语义管理」（`/semantic` 会跳到 `/semantic/domain`），代码在 `src/views/semantic` 与 `src/views/metric`。

| 概念 | 说明 | Controller |
| --- | --- | --- |
| **域（Domain）** | 业务主题分组，如"交易""用户"；Agent 会读取域名与描述，用它先判断该去哪些域找表 | `DomainController` |
| **逻辑表** | 给物理表补充业务语义，含领域、表描述、数据粒度（一行代表什么）、可见性、物理表是否存在 | `TableSemanticController` |
| **逻辑列** | 给物理列补充业务含义和语义类型（标识、维度、度量、时间、地点）；同步缓存会保存物理类型、主键与索引提示，供 Agent 生成 SQL 前参考 | `TableColumnSemanticController` |
| **表关系** | 表间 join 路径（一对一/一对多）、启禁状态，供 Agent 多表查询时自动拼 SQL | `TableRelationSemanticController` |
| **关系工作区** | 全局视角查看所有数据源的表关系，支持分页、关键词过滤、按 enabled 筛选 | `TableRelationWorkspaceController` |
| **指标口径** | 指标的业务标识（`metric_key`）、公式、时间字段、过滤，以及计算方式、结果类型、单位、倍率和币种。支持逻辑删除（`is_deleted`），前端在「语义管理 / 指标口径」维护 | `MetricController` |
| **同步** | 拉取物理表结构变更到语义层、刷新物理表存在状态 | `TableSemanticSyncController` |

## 3. 同步机制

- `SchemaReader` 通过 JDBC `DatabaseMetaData` 读取表、列、主键和索引信息，不依赖特定数据库方言。
- `service/semantic/sync/*` 将物理表结构同步到 `table_info` / `column_info`，用于形成 Agent 可读取的语义快照；字段同步会写入 `type_name`、`primary_key`、`index_info`、物理描述与物理存在状态。
- 同步不会覆盖已有人工口径：已有 `column_description` 会优先保留，物理描述只作为缺省补充。
- `SemanticAvailabilityHelper` + `ColumnInvalidReasonEnum` / `TableInvalidReasonEnum` 标注不完整或物理缺失的语义信息，提示管理员补齐。
- `SemanticMergeService` 负责把物理信息与人工编辑合并成管理界面展示的数据。

## 4. Agent 使用方式

Agent 查询前按需调用语义工具：

1. `get_domains`：返回可用领域的 `name` 与 `description`，让模型先选业务域。
2. `get_tables(domains=[...])`：返回同步后的表语义，包括表名、领域、描述、数据粒度与已启用表关系。数据粒度说明一行代表的业务实体或事件，例如“每行代表一份订单”。
3. `get_table_schema(table_name=...)`：返回同步后的字段信息，包括字段名、物理类型、语义类型、主键、索引提示和字段描述。
4. `get_metric_caliber(hint=...)`：返回匹配指标的表达式、过滤、时间字段，以及计算方式、结果类型、标准单位、倍率和币种，供 Agent 确认计算与展示规则。

指标工具根据当前会话绑定的数据源检索，可以直接传入用户整句问题。当前按名称和拆分后的别名做不区分大小写的包含匹配，忽略不足2个字符的词，不匹配 `metricKey`。按命中词长度排序，最多保留3个候选；只返回第一名的完整口径，其余作为名称与 key 提示。未命中时最多列出5个已有指标，供进一步确认。

这条链路依赖语义层同步后的缓存。新增或变更业务库表结构后，请先在「语义管理 / 表语义」里同步物理表，再让 Agent 查询。

已有元数据库升级时，备份数据库后执行 `sql/migration_primary_key_relations.sql`。脚本会补齐旧库字段与索引，并将列和逻辑关系迁移到表主键引用。请在启动新版后端前完成迁移。全新安装无需额外执行。

指标业务属性同样通过该脚本补齐，每个新增字段均检查是否存在，可重复执行。已经完成主键关联升级的数据库也需要执行最新脚本，以新增 `calculation_type`、`value_type`、`unit`、`result_multiplier`、`currency_code`。旧指标的计算方式默认为 `EXPRESSION`，倍率默认为 `1`；结果类型、单位及币种保留未配置，原表达式不变。全新安装的 `sql/data_source.sql` 已包含这些字段。

## 5. 指标业务属性

指标管理支持以下属性：

| API 属性 | 数据库类型 | 配置规则 |
| --- | --- | --- |
| `calculationType` | `VARCHAR(32)` | `EXPRESSION` 普通表达式、`DISTINCT_COUNT` 去重计数、`RATIO` 分子分母计算、`SNAPSHOT` 快照，默认 `EXPRESSION`。当前仅描述口径，仍需维护完整的 `measureExpr` 和业务说明，不自动生成分子、分母或快照 SQL。 |
| `valueType` | `VARCHAR(16)` | `NUMBER` 普通数值、`MONEY` 金额、`PERCENT` 百分比。API兼容省略字段，旧指标的空值表示未配置。 |
| `unit` | `VARCHAR(32)` | 标准结果单位，最多32个字符，保存时去除首尾空白。普通数值、金额配置结果类型后必须填写；百分比由后端固定为 `1`，表示无量纲比值。 |
| `resultMultiplier` | `DECIMAL(24,12)` | 标准结果 = 度量表达式原始结果 × 倍率，默认 `1`，必须大于0，最多12位整数和12位小数。Java使用 `BigDecimal`；API以十进制字符串返回，前端也以字符串提交，避免浮点精度丢失。 |
| `currencyCode` | `VARCHAR(3)` | 金额必须填写 JDK 认可的3位币种代码，保存时转为大写；其他结果类型保存时清除币种。仅表示结果币种，不自动执行换汇。 |

例如 `SUM(amount_cent)` 的单位配置为元、倍率配置为 `0.01`、币种配置为 `CNY`；若表达式已除以100，则倍率配置为 `1`，避免重复转换。百分比表达式应返回比值，倍率必须为 `1`，例如 `0.1234` 展示为 `12.34%`，不要提前乘以100。多币种数据必须先过滤到一种币种，或明确表达式中的换汇规则。

工具会提示去重计数按目标区间重新去重、比率重新计算分子分母、快照不可跨时间直接累加。当前没有自动改写或强制校验最终 SQL 的业务聚合能力；零分母、快照时点与缺失处理仍须在表达式和说明中定义，信息不足时由 Agent 澄清。

### 管理界面

入口为「语义管理 / 指标口径」。列表展示五个业务属性；新增与编辑表单位于 `data-agent-frontend/src/views/metric/components/MetricEditDialog.vue`。

- 新建表单默认普通表达式、普通数值、倍率 `1`，填写单位后保存；编辑旧指标时保留未配置的结果类型。
- 选择百分比后，表单将单位设为 `1`、倍率设为 `1`，并禁用这两个输入。切换为其他类型后，需确认单位和倍率符合当前表达式。
- 金额类型显示币种输入；切换为其他类型时清除币种。后端同样校验币种及百分比倍率，直接调用API也受这些规则约束。
- 当前列表关键词搜索只过滤已加载的当前页，可匹配名称、key及同义词，不是跨页搜索。

### API 创建与更新

`POST /api/metric` 创建指标，`PUT /api/metric/{id}` 更新指标，详情与分页查询继续返回上述属性。创建时 `datasourceId`、`metricKey`、`name` 必填；更新时 `name` 必填，不允许修改 `metricKey` 或数据源。

下面的创建请求表示将分换算为元，币种保存后归一化为 `CNY`，倍率响应为十进制字符串：

```json
{
  "datasourceId": 1,
  "metricKey": "sales",
  "name": "销售额",
  "measureExpr": "SUM(amount_cent)",
  "calculationType": "EXPRESSION",
  "valueType": "MONEY",
  "unit": "元",
  "resultMultiplier": "0.01",
  "currencyCode": "cny"
}
```

更新通过 MapStruct 忽略未传或为 `null` 的字段，保留原值，再按最终结果类型执行校验和归一化。可选文本可以通过空串清空，但已配置普通数值或金额的单位不能清空；结果类型不能用空串重置。金额切为普通数值或百分比时，即使未传 `currencyCode`，也会清除原币种。`unit` 与 `currencyCode` 的持久化配置允许写入 `NULL`，确保清空能实际落库。

## 6. 最佳实践

- **先接数据源，再配语义**：语义依附于物理表，顺序不要反。
- **结构变更后先同步**：字段类型、主键、索引提示来自物理库同步；旧缓存会让 Agent 拿不到最新 schema。
- **优先配置高频指标口径**：把最常问、最容易错的指标（销售额、退款、复购等）口径显式化，收益最高。
- **关系要准**：多表查询的准确率高度依赖表关系配置。
- **把"问不到"当信号**：用户问不到的指标，往往暴露了语义层/口径的缺口，是后续完善语义层的重要线索。

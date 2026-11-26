# 语义层

语义层是 Data Agent 把"业务语言"翻译成"数据表示"的核心桥梁。没有它，LLM 只能凭字段名瞎猜；有了它，LLM 才知道"销售额"对应哪张表、哪个字段、"上个月"用 `settle_time` 还是 `create_time`。

## 1. 为什么需要语义层

业务人员说的词（"流水""GMV""复购率"）和数据库里的字段（`paid_amount`、`status`）往往对不上。直接让 LLM 猜，会出现：

- 字段/表名猜错 → SQL 报错或查出空结果；
- 口径不一致 → 不同人问"销售额"得到不同数字；
- 时间维度错用 → 用下单时间而非结算时间，统计口径全错。

语义层把这些"业务 ↔ 数据"的映射显式沉淀下来，让 Agent 每次答题时按需取用，稳定且可审计。

## 2. 语义层的组成

管理操作的权限以各 Controller 的 `@RequirePermission` 标记为准。指标读取、创建、更新和删除分别要求 `semantic:metric:read`、`semantic:metric:create`、`semantic:metric:update`、`semantic:metric:delete`；仅登录不足以访问。表列、关系管理还检查当前用户的数据范围，结构同步要求 `semantic:table:sync`。前端入口是「语义管理」（`/semantic` 会跳到 `/semantic/domain`），代码在 `src/views/semantic` 与 `src/views/metric`。完整规则见 [permissions.md](permissions.md)。

| 概念 | 说明 | Controller |
| --- | --- | --- |
| **域（Domain）** | 业务主题分组，如"交易""用户"；Agent 会读取域名与描述，用它先判断该去哪些域找表 | `DomainController` |
| **逻辑表 / 普通视图** | 给物理表或普通视图补充业务语义，含对象类型（`TABLE` / `VIEW`）、领域、描述、数据粒度（一行代表什么）、业务唯一键、可见性、物理对象是否存在 | `TableSemanticController` |
| **逻辑列** | 给物理列补充业务含义和语义类型（标识、维度、度量、时间、地点）；同步缓存会保存物理类型、主键与索引提示，供 Agent 生成 SQL 前参考 | `TableColumnSemanticController` |
| **表关系** | 表间 join 路径（一对一/一对多）、启禁状态，供 Agent 多表查询时自动拼 SQL | `TableRelationSemanticController` |
| **关系工作区** | 按所选数据源分页查看表与关系画布，支持关键词与排序；仅返回两端都位于当前页且可见的关系 | `TableRelationWorkspaceController` |
| **指标口径** | 指标的业务标识（`metric_key`）、公式、时间字段、过滤，以及计算方式、结果类型、单位、倍率和币种。支持逻辑删除（`is_deleted`），前端在「语义管理 / 指标口径」维护 | `MetricController` |
| **同步** | 拉取物理表结构变更到语义层、刷新物理表存在状态 | `TableSemanticSyncController` |

## 3. 同步机制

- `SchemaReader` 通过 JDBC `DatabaseMetaData` 读取 `TABLE` 和 `VIEW` 对象及其输出列；物理表同时读取主键与索引，普通视图跳过主键和索引读取。
- `service/semantic/sync/*` 将物理表结构同步到 `table_info` / `column_info`，用于形成 Agent 可读取的语义快照；字段同步会写入 `type_name`、`primary_key`、`index_info`、物理描述与物理存在状态。
- 同步不会覆盖已有人工口径：已有 `column_description` 会优先保留，物理描述只作为缺省补充；管理员配置的业务唯一键同样保留。
- `SemanticAvailabilityHelper` + `ColumnInvalidReasonEnum` / `TableInvalidReasonEnum` 标注不完整或物理缺失的语义信息，提示管理员补齐。
- `SemanticMergeService` 负责把物理信息与人工编辑合并成管理界面展示的数据。
- `SemanticSyncDiffService` 根据写入前的表列快照计算新增、更新、恢复与缺失；`SemanticSyncApplyService` 在事务中保存物理缓存、标记缺失，并保留已有人工描述。统计结果基于原快照生成，不把刚写入的数据当作原数据比较。
- 同步和「刷新物理状态」有不同作用：同步会新增、更新并恢复所选物理表列；刷新状态只标记已缺失的表列，不导入新字段或恢复存在状态。物理表列恢复后请重新同步。

| 接口 | 用途 |
| --- | --- |
| `GET /api/semantic/tables/sync/candidates` | 按 `datasourceId` 获取表与普通视图候选，响应含 `objectType`，可分页、关键词过滤与排序 |
| `POST /api/semantic/tables/sync` | 同步选定表或普通视图的物理结构与语义缓存 |
| `POST /api/semantic/tables/sync/physical-status` | 刷新物理缺失状态 |
| `GET /api/semantic/tables/relations/workspace` | 按 `datasourceId`、`page`、`pageSize`、`keyword`、`sortOrder` 获取关系画布 |

关系画布由 `RelationWorkspaceService` 组装当前页表节点和列，按同一数据权限范围过滤列与关系；跨页关系不在当前画布展示。普通关系列表与画布的查询参数不同，画布不提供 `enabled` 筛选参数。

### 表与普通视图

在「语义管理 → 表语义 → 同步表或视图」选择对象；列表、同步候选、关系画布、角色授权和新生成的数据证据会区分对象类型。接口继续沿用 `tables` 路径和 `tableName` 字段，普通视图与表共用领域、描述、粒度、业务唯一键、可见性及关系管理。

视图字段来自视图的输出列，不推断底表主键、索引或业务唯一键，也不展开视图定义追溯底表血缘。新证据以实际查询的视图对象记录来源。底表权限和隐藏列不会自动继承到视图，需单独授权视图及其输出列，见 [普通视图权限](permissions.md#普通视图)。

升级时先执行 `sql/migration.sql`，为 `table_info` 增加 `object_type`；历史对象默认 `TABLE`，再次完整同步后更新实际类型。刷新物理状态不能替代完整同步，也不会恢复已标记缺失的对象。历史证据不补写对象类型。

当前仅支持连接所选 catalog/schema 范围内的普通视图，不额外支持物化视图或跨库对象发现。连接账号需要具备对应元数据与查询权限；CSV 数据源只允许已就绪的导入表，不开放手工视图。

### 业务唯一键

在「语义管理 → 表语义 → 编辑」中选择一列或一组字段，声明它们共同唯一标识一行。例如订单表的 `tenant_id` 与 `order_no` 可以组成一个业务唯一键。表和普通视图都支持配置，每个对象维护一组键字段。

业务唯一键是管理员声明的业务语义，独立于同步得到的物理主键和索引。保存配置不会创建数据库约束，也不会查询数据验证唯一性或非空性；使用前需确认字段组合确实符合业务粒度。

- 先同步对象及其字段，再从当前存在的字段中选择。字段名不能为空或重复，匹配时忽略首尾空白和大小写，保存时保留已同步字段的实际名称。
- API 提交字段实际名称，不要附加表名前缀、表达式或 SQL 定界符；字段名本身包含的双引号、反引号会保留。
- 同步和刷新物理状态都保留键配置。对象或键字段被标记为物理缺失时，列表显示失效原因；可以清空或重新选择键，也可以保留原键只修改描述等信息。恢复对象或字段后需完整同步。
- 重置表语义会清空业务唯一键。对已标记物理缺失的对象执行重置，还会删除该对象的表列语义及关联逻辑关系。
- 编辑所需权限和组合键的整体过滤规则见 [业务唯一键权限](permissions.md#业务唯一键)。

`PUT /api/semantic/tables` 在原表语义请求中增加 `businessKeyColumns`，例如：

```json
{
  "datasourceId": 1,
  "tableName": "orders",
  "domain": "trade",
  "tableDescription": "订单",
  "dataGranularity": "每行代表一份订单",
  "businessKeyColumns": ["tenant_id", "order_no"],
  "isVisible": true
}
```

仅 `businessKeyColumns` 支持省略或传 `null` 时保留原配置，传 `[]` 清空，传非空数组替换整组键。其他表语义字段仍按原更新规则处理，不要把此请求当作所有字段均可省略的局部更新。

`GET /api/semantic/tables` 的每条记录增加两个属性：`businessKeyColumns` 是完整键字段列表，未配置时为 `[]`，任一键字段未授权时为 `null`；`businessKeyInvalidReason` 是物理对象或键字段缺失的原因，无物理失效或无权读取该键时为 `null`。这些状态以最近同步或刷新的物理缓存为准。

升级旧元数据库需在启动新版后端前执行最新的 `sql/migration.sql`，为 `table_info` 增加 `business_key_columns_json`。历史对象默认未配置业务唯一键；新环境的 `sql/data_source.sql` 已包含该字段。

## 4. Agent 使用方式

Agent 查询前按需调用语义工具：

1. `get_datasources`：列出当前会话可用的数据源；存在多个数据源时先调用它。
2. `get_domains(datasource_id=...)`：返回可用领域的 `name` 与 `description`，让模型先选业务域。
3. `get_tables(datasource_id=..., domains=[...])`：返回同步后的表与普通视图语义，包括名称、对象类型、领域、描述、数据粒度、业务唯一键（`businessKeyColumns`）与已启用关系。数据粒度说明一行代表的业务实体或事件，例如“每行代表一份订单”。
4. `get_table_schema(datasource_id=..., table_name=...)`：返回同步后的字段信息，包括字段名、物理类型、语义类型、主键、索引提示和字段描述。
5. `get_metric_caliber(datasource_id=..., hint=...)`：返回匹配指标的表达式、过滤、时间字段，以及计算方式、结果类型、标准单位、倍率和币种，供 Agent 确认计算与展示规则。

上述工具使用当前会话允许的数据源与可信用户上下文；除列出数据源的 `get_datasources` 外，存在多个数据源时都必须传 `datasource_id`。领域、表、列和关系结果会按授权范围过滤；SQL 执行仍需再次授权，不能通过跳过 schema 工具绕过表列限制。

`get_tables` 只在整组键字段均已同步、物理存在、对 AI 可见且当前用户有权读取时返回该组业务唯一键；未配置或任一条件不满足时返回 `businessKeyColumns: []`，不会把组合键截成部分字段。工具将其作为管理员声明提供给 Agent，不作为数据库已保证唯一性的证明。

指标工具按 `datasource_id` 检索，支持 `metric_key` 精确查询和 `hint` 名称/别名查询，至少提供一项，key 优先。key 未找到时不会回退到相似指标；同一数据源出现重复有效 key 时返回配置冲突。原有 `hint` 调用兼容，单指标可以传整句问题，多指标应分别查找。

名称和别名统一做 NFKC 全半角归一化、大小写折叠、首尾及连续空白处理。别名支持逗号、顿号、分号、斜杠、竖线和换行，保存时去重；英文短语中的空格不拆分。英文缩写检查字母、数字和下划线边界，中文句子可直接包含英文缩写。单字词仅允许完整相等，不参与整句包含匹配。同一位置优先更具体的长词，同一个词命中不同指标仍需澄清。

工具元数据提供 `lookupStatus`（`RESOLVED`、`AMBIGUOUS`、`NOT_FOUND`）、`requiresClarification`、`candidates` 和 `candidateCount`。只有 `RESOLVED` 返回完整口径，并在 `metrics` 中记录已确定指标；候选不记为已确定引用。最多展示3个候选，歧义判断基于全部候选。多个独立指标返回 `AMBIGUOUS` 且 `requiresClarification=false`，调用方应按名称拆开查询；同名或共享别名冲突返回 `requiresClarification=true`，不能自动采用第一名。

严格澄清状态持久化到 `metric_clarification`，按用户与会话隔离。出现冲突后，SQL、Python、图表、报告和导出工具统一拒绝执行，返回 `METRIC_CLARIFICATION_REQUIRED`。模型自行改传候选 key 或查询另一个指标不能解除阻塞。聊天通过现有 `ask_user` 展示服务端生成、顺序固定的候选，只有回复该次提问的编号或完整 key 才能确认；普通聊天输入及其他提问的回复不能确认指标。确认后还必须用所选 `metric_key` 重新取得 `RESOLVED`。状态仅保存候选 ID、key、口径版本和提问 ID。确认及重读都会检查当前指标和权限；口径变化会保存新候选并要求重新确认；删除的候选会移除，全部删除时清空阻塞状态。删除聊天也会清空待选内容。

外部 REST 客户端在取得用户明确选择后，使用相同 `X-Session-Id` 调用 `POST /agent/metric-choice`，请求体为 `{ "metricKey": "paid_sales" }`，再以 `metric_key` 查询。候选在确认前发生变化时接口返回 `{ "value": false }`，此时应重新查询并请用户再次选择；成功返回 `{ "value": true }`。确认接口不作为模型工具暴露。CLI 对应 `data-agent-cli tools confirm-metric paid_sales --session-id <session>`。外部应用负责向实际用户展示选择，不能把模型推测当成用户确认。

部署前执行 `sql/migration.sql` 创建澄清状态表；新环境使用 `sql/data_source.sql`。未命中时最多列出5个已有指标作为建议，不能自动套用建议口径。

这条链路依赖语义层同步后的缓存。新增或变更业务库表结构后，请先在「语义管理 / 表语义」里同步物理表，再让 Agent 查询。

已有元数据库升级时，备份数据库后执行 `sql/migration.sql`。脚本会补齐旧库字段与索引、会话数据源范围和审计字段，并将列和逻辑关系迁移到表主键引用。请在启动新版后端前完成迁移。全新安装无需额外执行。

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

CSV 托管数据源的指标必须用已就绪的物理表名限定字段，例如 `SUM(csv_t_<实际后缀>.c001)`；不能直接沿用上例的未限定字段，也不要增加数据库名前缀。具体使用与删除引用规则见 [CSV 导入](csv-import.md)。

## 6. 最佳实践

- **先接数据源，再配语义**：语义依附于物理表，顺序不要反。
- **结构变更后先同步**：字段类型、主键、索引提示来自物理库同步；旧缓存会让 Agent 拿不到最新 schema。
- **优先配置高频指标口径**：把最常问、最容易错的指标（销售额、退款、复购等）口径显式化，收益最高。
- **关系要准**：多表查询的准确率高度依赖表关系配置。
- **把"问不到"当信号**：用户问不到的指标，往往暴露了语义层/口径的缺口，是后续完善语义层的重要线索。

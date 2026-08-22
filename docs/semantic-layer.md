# 语义层

语义层是 Data Agent 把"业务语言"翻译成"数据表示"的核心桥梁。没有它，LLM 只能凭字段名瞎猜；有了它，LLM 才知道"销售额"对应哪张表、哪个字段、"上个月"用 `settle_time` 还是 `create_time`。

## 1. 为什么需要语义层

业务人员说的词（"流水""GMV""复购率"）和数据库里的字段（`paid_amount`、`status`）往往对不上。直接让 LLM 猜，会出现：

- 字段/表名猜错 → SQL 报错或查出空结果；
- 口径不一致 → 不同人问"销售额"得到不同数字；
- 时间维度错用 → 用下单时间而非结算时间，统计口径全错。

语义层把这些"业务 ↔ 数据"的映射显式沉淀下来，让 Agent 每次答题时按需取用，稳定且可审计。

## 2. 语义层的组成

所有操作均需 `@AdminOnly` 权限。前端入口是「语义管理」（`/semantic`），代码在 `src/views/semantic`。

| 概念 | 说明 | Controller |
| --- | --- | --- |
| **域（Domain）** | 业务主题分组，如"交易""用户"；Agent 会读取域名与描述，用它先判断该去哪些域找表 | `DomainController` |
| **逻辑表** | 给物理表起一个业务名，含领域、表描述、可见性、物理表是否存在 | `TableSemanticController` |
| **逻辑列** | 给物理列补充业务含义；同步缓存会保存字段类型、主键与索引提示，供 Agent 生成 SQL 前参考 | `TableColumnSemanticController` |
| **表关系** | 表间 join 路径（一对一/一对多）、启禁状态，供 Agent 多表查询时自动拼 SQL | `TableRelationSemanticController` |
| **关系工作区** | 全局视角查看所有数据源的表关系，支持分页、关键词过滤、按 enabled 筛选 | `TableRelationWorkspaceController` |
| **指标口径** | 一个指标"怎么算才对"的精确规定——`metric_key` 唯一标识、公式、时间维度、过滤、币种。支持逻辑删除（`is_deleted`），前端在「语义管理 / 指标口径」维护 | `MetricController` |
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
2. `get_tables(domains=[...])`：返回同步后的表语义，包括表名、领域、描述与已启用表关系。
3. `get_table_schema(table_name=...)`：返回同步后的字段信息，包括字段名、类型、主键、索引提示和字段描述。

这条链路依赖语义层同步后的缓存。新增或变更业务库表结构后，请先在「语义管理 / 表语义管理」里同步物理表，再让 Agent 查询。

## 5. 最佳实践

- **先接数据源，再配语义**：语义依附于物理表，顺序不要反。
- **结构变更后先同步**：字段类型、主键、索引提示来自物理库同步；旧缓存会让 Agent 拿不到最新 schema。
- **优先配置高频指标口径**：把最常问、最容易错的指标（销售额、退款、复购等）口径显式化，收益最高。
- **关系要准**：多表查询的准确率高度依赖表关系配置。
- **把"问不到"当信号**：用户问不到的指标，往往暴露了语义层/口径的缺口，是后续完善语义层的重要线索。

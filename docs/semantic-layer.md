# 语义层

语义层是 Data Agent 把"业务语言"翻译成"数据表示"的核心桥梁。没有它，LLM 只能凭字段名瞎猜；有了它，LLM 才知道"销售额"对应哪张表、哪个字段、"上个月"用 `settle_time` 还是 `create_time`。

## 1. 为什么需要语义层

业务人员说的词（"流水""GMV""复购率"）和数据库里的字段（`paid_amount`、`status`）往往对不上。直接让 LLM 猜，会出现：

- 字段/表名猜错 → SQL 报错或查出空结果；
- 口径不一致 → 不同人问"销售额"得到不同数字；
- 时间维度错用 → 用下单时间而非结算时间，统计口径全错。

语义层把这些"业务 ↔ 数据"的映射显式沉淀下来，让 Agent 每次答题时按需取用，稳定且可审计。

## 2. 语义层的组成

所有操作均需 `@AdminOnly` 权限。前端管理界面在 `src/views/semantic`。

| 概念 | 说明 | Controller |
| --- | --- | --- |
| **域（Domain）** | 业务主题分组，如"交易""用户"，用于缩小表检索范围 | `TableSemanticController` |
| **逻辑表** | 给物理表起一个业务名，含表描述、可见性、物理表是否存在 | `TableSemanticController` |
| **逻辑列** | 给物理列补充业务含义、枚举值、单位、币种 | `TableColumnSemanticController` |
| **表关系** | 表间 join 路径（一对一/一对多）、启禁状态，供 Agent 多表查询时自动拼 SQL | `TableRelationSemanticController` |
| **关系工作区** | 全局视角查看所有数据源的表关系，支持分页、关键词过滤、按 enabled 筛选 | `TableRelationWorkspaceController` |
| **指标口径** | 一个指标"怎么算才对"的精确规定——`metric_key` 唯一标识、公式、时间维度、过滤、币种。支持逻辑删除（`is_deleted`） | `MetricController` |
| **同步** | 拉取物理表结构变更到语义层、刷新物理表存在状态 | `TableSemanticSyncController` |

## 3. 同步机制

- `service/semantic/sync/*` 拉取物理表结构变更为"待审核"的语义变更，不直接覆盖人工口径。
- `SemanticAvailabilityHelper` + `ColumnInvalidReasonEnum` / `TableInvalidReasonEnum` 标注不完整的语义信息，提示管理员补齐。
- `SemanticMergeService` 处理物理信息、AI 建议与人工编辑的合并策略。

## 4. 最佳实践

- **先接数据源，再配语义**：语义依附于物理表，顺序不要反。
- **优先配置高频指标口径**：把最常问、最容易错的指标（销售额、退款、复购等）口径显式化，收益最高。
- **关系要准**：多表查询的准确率高度依赖表关系配置。
- **把"问不到"当信号**：用户问不到的指标，往往暴露了语义层/口径的缺口，是后续完善语义层的重要线索。

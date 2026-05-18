# 语义层大数据量分页优化方案

生成时间：2026-05-10  
范围：当前语义层分页在大数据量场景下的后端成本问题调研与优化方案  
对象：表语义分页、列语义分页、逻辑关系分页、schema 详情分页

---

# 一、问题定义

当前系统已经引入了分页返回，但分页主要发生在：

> **全量读取 -> 全量 merge / 映射 -> 最后切页返回**

这意味着当前分页优化只解决了：
- 返回体大小
- 网络传输量

但没有真正解决：
- 后端 IO 压力
- 后端 CPU 压力
- 后端内存占用
- 大 schema 下的响应时间波动

也就是说：

> **当前分页只闭了“响应体变小”的一半问题，没有闭“后端大数据量处理成本”这一半问题。**

---

# 二、当前代码中的真实瓶颈位置

## 1. 表语义分页

### 当前实现
`SemanticSchemaService.getTablePage(...)`

当前逻辑本质上是：
1. 先拿整个 datasource 的物理表：`mergeTables(datasource)`
2. 再把所有结果映射成 `TableSemanticResponse`
3. 最后 `PageResponse.from(...)` 切页

### 问题
即使只请求第 1 页 20 条，也会：
- 读全量表
- merge 全量表
- 构造全量 response
- 最后只返回其中一页

### 成本
- **IO**：扫描完整物理表元数据
- **CPU**：全量 merge + 全量 DTO 映射
- **内存**：保存全量 merged list + 全量 response list

---

## 2. 列语义分页

### 当前实现
`SemanticSchemaService.getColumnPage(...)`

当前逻辑本质上是：
1. 先 `mergeColumns(datasource, tableName)`
2. 再全量映射为 `ColumnSemanticResponse`
3. 最后 `PageResponse.from(...)`

### 问题
对于超宽表（几百上千列）时：
- 仍然要先拿全量物理列
- 全量 merge
- 全量映射
- 再切页

### 成本
- 宽表场景下 CPU / 内存压力明显
- 如果 `SchemaReader.getTableSchema(...)` 本身较慢，会直接拖慢分页接口

---

## 3. 逻辑关系分页

### 当前实现
`LogicalTableRelationServiceImpl.listByDatasourceIdAndTable(...)`

当前逻辑本质上是：
1. `logicalTableRelationMapper.selectByDatasourceId(datasourceId)`
2. 在内存中过滤 source table
3. 对每条关系做 `mapResponse(...)`
4. 再 `PageResponse.from(...)`

### 问题
这意味着：
- 即使只看某张表的关系，也会先查某数据源下全量逻辑关系
- 即使只看 20 条，也会先全量映射、全量有效性评估

### 成本
- **DB 成本**：查太多无关关系
- **CPU 成本**：每条都做 JSON decode + 可见性评估
- **内存成本**：全量响应对象堆积

---

## 4. `get_table_schema` 的列 / 关系分页

### 当前实现
`SemanticSchemaService.getTableSchema(...)`

当前逻辑本质上是：
1. 先全量 `mergeColumns(...)`
2. 先全量 `mergeVisibleRelations(...)`
3. 最后分别 `PageResponse.from(...)`

### 问题
分页仍然只发生在最终结构层，前面的物理读取、merge、过滤、映射全部是全量完成。

### 成本
- 对超宽表、关系很多的表仍然无法真正按页降成本

---

# 三、为什么当前方案在大数据量下不够

当前分页方案是典型的：

> **表现层分页，而不是数据层分页。**

## 优点
- 接入快
- 不需要改底层读取逻辑
- 返回协议统一
- 短期内可以降低前端一次拿到的数据量

## 缺点
- 数据量大时后端性能几乎不降
- 只是“切给前端少一点”，不是“后端少算一点”
- 不适合：
  - 大数据源表很多
  - 单表列很多
  - 某数据源逻辑关系很多
  - 后续继续叠加高级语义字段

---

# 四、优化思路总览

我建议把优化拆成三层：

## Layer 1：数据库下推分页
尽量在 mapper / SQL 层先减少记录数量。

## Layer 2：延迟 merge / 局部 merge
不要为了第 1 页 20 条，先 merge 全量 5000 条。

## Layer 3：缓存 / 预计算 / 异步化
对物理 schema 这种低频变化、高频读取的对象做缓存或预热。

这三层不是互斥的，而是逐步叠加。

## 当前推荐设计口径：按页懒加载，而不是逐条懒加载

这里的“懒加载”不建议理解成：
- 用到 1 条查 1 条
- 用到 1 个字段查 1 次
- 每个 DTO 都单独回源一次

那样虽然避免了“第一页 20 条先 merge 5000 条”，但很容易把问题从“全量 merge”切换成新的 `N+1` 回源问题。

当前项目更适合的落法是：

> **按页懒加载 + 页内批量 merge + 物理基线缓存**

也就是：
1. 先按 `page/pageSize` 确定当前页真正需要哪些表 / 列 / 关系
2. 只为这一页对象做语义 overlay / merge
3. 页内仍保持批量查询，不做逐条回源
4. 物理 schema 不反复全量扫描，而是先走 datasource 级 / table 级缓存

这样既能避免“为 20 条先算 5000 条”，也能避免“为 20 条发 20 次小查询”。

## 当前缓存建议：先本地缓存，不把 Redis 作为前置条件

当前阶段缓存主要用于降低：
- `SchemaReader.getTables(...)` 的重复元数据读取
- `SchemaReader.getTableSchema(...)` 的重复元数据读取
- `SchemaReader.getImportedRelations(...)` 的重复元数据读取

由于这些对象的特点是：
- 读多写少
- 变化频率低
- 当前主要服务单体后端内的重复访问

所以当前建议优先采用：
- 进程内本地缓存
- TTL + 手动失效
- datasource 级 / table 级 key 设计

而不是一开始就引入 Redis。

Redis 更适合后续这些场景：
- 后端多实例部署
- 多节点重复扫同一批 schema
- 冷启动成本已经成为瓶颈
- 本地缓存命中率不足且需要跨实例共享

---

# 五、推荐方案（按收益和改造成本排序）

## 方案 A：逻辑关系分页先改成真正数据库分页

### 为什么优先改它
这是当前最容易改、收益最大的部分。

因为逻辑关系数据已经在你自己的库里，完全受控，不像物理 schema 要经过 JDBC 元数据读取。

### 当前问题
现在是：
- `selectByDatasourceId(datasourceId)`
- 内存里筛 `source_table_name`
- 内存里 map / evaluate
- 再切页

### 推荐改法
新增 mapper 方法：
- `countByDatasourceIdAndSourceTable(...)`
- `selectPageByDatasourceIdAndSourceTable(...)`

SQL 先按：
- `datasource_id`
- `source_table_name`
- 可选 keyword / enabled

直接分页：
- `LIMIT offset, pageSize`

### 然后 service 层变成
1. 先 count
2. 再只查当前页记录
3. 只对当前页做 `mapResponse(...)`
4. 返回 `PageResponse.of(...)`

### 效果
- DB 只返回当前页关系
- JSON decode 数量下降
- 可见性评估数量下降
- 内存大幅下降

### 注意点
`effective / invalidReason` 仍然需要逐条算，但只算当前页即可。

---

## 方案 B：表语义分页改成“分段 merge”而不是“全量 merge”

这是最关键但也最难的一块。

### 当前难点
表语义列表来自两部分：
1. 物理表（`SchemaReader.getTables`）
2. 表语义表（`table_info`）

而且要按表名 merge。

### 不能直接简单 SQL 分页的原因
物理表不在你自己的数据库里，而是来自 JDBC 元数据读取。  
所以无法直接在 MyBatis 里对“物理表 + 语义表合并结果”做 SQL 分页。

### 推荐改法
把它拆成两步：

#### Step 1：物理表清单做缓存基线
为每个 datasource 缓存：
- 物理表名列表
- 物理表描述
- 更新时间戳 / cache version

缓存形态可先做进程内缓存。

#### Step 2：分页时只构造当前页的 merge 结果
思路：
1. 先拿“物理表基线列表”
2. 先确定当前页需要哪些表名
3. 再只查这些表名对应的 `table_info` 语义记录
4. 只对当前页表做 merge 和 DTO 映射

### 关键变化
你不再：
- 全量构造 `TableMergeSnapshot`
- 全量 map 成 `TableSemanticResponse`

而是：
- 全量最多只保留一个轻量物理表索引 / 缓存
- 当前请求只 merge 当前页表

这本质上就是：

> **按页懒加载，而不是按表逐条懒加载。**

### 效果
- CPU 从“每次全量 merge”变成“每次只 merge 一页”
- 内存从“全量响应对象”变成“页级响应对象”
- IO 仍然有物理表基线获取成本，但可通过缓存摊平

### 进一步优化
如果表量特别大，还可以：
- 给 `getTables` 基线增加 TTL 缓存
- 或在 datasource 切换 / 手动刷新时主动预热

---

## 方案 C：列语义分页改成“物理列缓存 + 当前页 merge”

### 当前问题
列分页本质和表分页类似：
- 物理列来自 `SchemaReader.getTableSchema(...)`
- 语义列来自 `column_info`
- 现在是全量 merge 后再切页

### 推荐改法
#### Step 1：缓存表级物理列基线
缓存 key：
- `datasourceId + tableName`

缓存内容：
- 物理列列表
- 主键信息
- remarks
- defaultValue
- nullable
- type

#### Step 2：分页时只 merge 当前页列
1. 从缓存拿物理列基线
2. 先确定当前页列名
3. 再只查这些列名对应的 `column_info`
4. 只构造当前页 `ColumnSemanticResponse`

这里同样不建议做成“每列懒查一次”，而是：

> **按页确定列集合，再做页内批量 merge。**

### 效果
- 避免每次全表列元数据重新读取
- 避免每次全量 merge 宽表所有列
- 对超宽表效果明显

### 代价
需要新增：
- `findByDatasourceIdAndTableNameAndColumnNames(...)`
- 或类似按列名批量查询语义记录的方法

---

## 方案 D：`get_table_schema` 的分页语义调整

### 当前问题
`get_table_schema` 现在带分页，但语义上它更像“表详情接口”。

如果调用方真正需要的是完整表结构，那么分页的意义有限；如果调用方只需要前 20 列，那分页才有价值。

### 推荐做法
#### 1. 明确两类场景
- **场景 A：AI 推理**
  - 默认倾向拿更多完整结构
- **场景 B：管理端浏览**
  - 可以按页浏览列和关系

#### 2. 拆分接口职责
推荐中长期考虑：
- `get_table_schema`：偏完整详情
- 新增：
  - `get_table_columns_page`
  - `get_table_relations_page`

这样可以避免一个接口既想当详情，又想当浏览器分页接口。

### 短期可接受方案
如果暂不拆接口，也建议：
- `get_table_schema` 内部仍走缓存基线
- 对列和关系的分页只对当前页做映射

---

## 方案 E：候选表 / 候选字段接口也应考虑大数据量

当前：
- `listCandidateTables` 也是全量 mergeTables 再返回
- `listCandidateColumns` 也是全量 mergeColumns 再返回

### 风险
一旦：
- 可见表很多
- 表列很多

关系维护页面的下拉选择也会卡。

### 推荐改法
#### 候选表
- 支持 keyword 搜索
- 支持分页
- 先查物理表基线 / 缓存
- 再只 merge 当前页候选表

#### 候选字段
- 支持 keyword 搜索
- 支持分页
- 基于物理列缓存 + 当前页 merge

### 注意
候选接口是 UI 交互热点，比后台全量列表更应该优先优化。

---

# 六、推荐的分阶段落地顺序

## 第一阶段已开始落地（2026-05-16）
- `SchemaReader` 已开始加入 datasource 级表缓存、table 级列缓存与关系缓存
- 表语义分页已开始改为：物理表缓存基线 + 当前页语义回填
- 列语义分页已开始改为：物理列缓存基线 + 当前页语义回填
- 候选表 / 候选字段已开始收口到 `keyword + page/pageSize + sortOrder`
- 关系列表已开始补最小 `keyword + enabled + sortOrder` 入口

---

## 第一阶段：低成本高收益
### 1. 逻辑关系分页下推到 DB
- 新增 mapper count/page 方法
- 改 `LogicalTableRelationServiceImpl.listByDatasourceIdAndTable`

### 2. 候选表 / 候选字段支持 keyword + 分页
- 至少从接口层开始限制返回范围

### 3. 给 `PageResponse` 增加真正 DB 分页用法约束
- 统一用 `PageResponse.of(items, total, pageRequest)`
- 少用 `PageResponse.from(allItems, pageRequest)`

### 4. 明确懒加载口径
- 后续分页优化默认采用“按页懒加载”
- 不采用“逐条回源式懒加载”

---

## 第二阶段：物理 schema 读取缓存化
### 1. datasource 级物理表缓存
### 2. table 级物理列缓存
### 3. 适度 TTL / 手动刷新机制

这一步会显著减少 JDBC metadata 反复扫描的成本。
缓存默认优先采用进程内本地缓存即可。

---

## 第三阶段：局部 merge 化
### 1. 表分页只 merge 当前页
### 2. 列分页只 merge 当前页
### 3. 关系分页只映射当前页

这一步才是真正把 CPU / 内存成本降下来。

---

## 第四阶段：架构分拆
### 1. 浏览分页接口 vs 完整详情接口拆分
### 2. Agent 详情接口与后台列表接口进一步分流

---

# 七、我推荐的最终方案

如果按你的当前项目现状和改造成本来选，我推荐：

## P0：立刻做
1. **逻辑关系分页改成数据库分页**
2. **候选表 / 候选字段接口加 keyword + page/pageSize**
3. **在代码规范上限制继续使用 `PageResponse.from(allItems, pageRequest)` 处理大列表**

## P1：下一步做
4. **给 `SchemaReader.getTables` 结果做 datasource 级本地缓存**
5. **给 `SchemaReader.getTableSchema` 结果做 table 级本地缓存**
6. **给 `SchemaReader.getImportedRelations` 结果做 table 级本地缓存**

## P2：继续演进
7. **把表分页和列分页改成“当前页 merge”模型**
8. **视需要拆分详情接口和浏览接口**

## 当前推荐实现口径
- 懒加载：按页懒加载
- merge：页内批量 merge
- 缓存：先本地缓存
- Redis：不是当前阶段前置条件

---

# 八、各接口的改造建议总结

## 1. 表语义分页
### 当前
- 全量物理表读取
- 全量 merge
- 全量 response 映射
- 最后切页

### 目标
- 物理表基线本地缓存
- 当前页 merge
- 当前页 response 映射

---

## 2. 列语义分页
### 当前
- 全量物理列读取
- 全量 merge
- 全量 response 映射
- 最后切页

### 目标
- 物理列基线本地缓存
- 当前页 merge
- 当前页 response 映射

---

## 3. 逻辑关系分页
### 当前
- 全量 selectByDatasourceId
- 内存 filter / map / evaluate
- 最后切页

### 目标
- DB count
- DB page query
- 当前页 evaluate
- `PageResponse.of(...)`

---

## 4. 候选表 / 候选字段
### 当前
- 全量返回

### 目标
- keyword + page/pageSize
- 尽量分页下推或局部 merge

---

# 九、文档结论

你指出的问题判断是准确的：

> 当前分页优化主要降低了返回体大小，但没有真正降低后端大数据处理成本。

如果目标是“数据量大时也能稳定处理”，必须把优化重点从：

- **结果切页**

转向：

- **数据层下推分页**
- **物理 schema 本地缓存**
- **局部 merge**
- **候选接口裁剪**

这是当前语义层进入大数据量可用阶段的关键一步。

---

# 十、建议下一步

如果你愿意，下一步我可以继续帮你做两种事之一：

1. **把这份方案再拆成一个可直接开发的任务清单**
2. **直接按优先级先改第一项：逻辑关系分页下推到 DB**

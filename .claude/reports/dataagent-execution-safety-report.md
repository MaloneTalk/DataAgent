# DataAgent SQL 安全与执行治理调研报告

生成时间：2026-05-10  
范围：SQL 校验、执行边界、成本保护、JSqlParser 参考方案

---

# 一、当前现状

当前 `SqlExecutor` 已具备：
- 仅允许 SELECT
- 30 秒超时
- 最多返回 200 行
- 基于正则的基础安全校验

但当前校验方式本质上仍是：
> **正则级安全边界**

这适合原型和开发阶段，但不足以支撑更复杂、更稳定的生产级执行治理。

---

# 二、当前主要风险

## 1. 正则校验鲁棒性有限
当前主要依赖：
- `^\s*SELECT\s`
- forbidden pattern

这对：
- 注释
- CTE (`WITH`)
- 方言差异
- 多语句边界
- 特殊空白与格式

的处理能力有限。

## 2. 只控结果，不控扫描成本
当前：
- `MAX_ROWS=200`
- `truncated=true`

这只能控制返回量，不能控制：
- 扫描多大表
- SQL 成本多高
- 是否会拖垮数据库

## 3. 没有审计与分级治理
当前缺：
- SQL 审计日志
- 风险分级
- 慢查询标记
- 查询白名单 / 黑名单策略

---

# 三、行业与工具参考

## 1. JSqlParser 的启发
JSqlParser 提供了两类很重要的能力：

### A. SQL 解析为 AST
可以把 SQL 解析成结构化语法树，而不是靠字符串猜。

### B. Validation / FeaturesAllowed.SELECT
可以明确验证：
- 这条 SQL 是否属于 SELECT 允许集合
- 是否超出设定功能边界

这说明：
> **相比当前正则策略，JSqlParser 更适合做“只读 SQL 执行边界”的下一阶段升级。**

---

# 四、推荐演进路径

## 第一阶段：保持现有正则校验，但补治理能力
先补：
- 审计日志
- 执行耗时
- datasource 维度记录
- truncated / timeout 标记

## 第二阶段：引入 AST 级校验
建议引入 JSqlParser 做：
- 解析 SQL
- 校验只允许 SELECT / WITH + SELECT
- 禁止 DDL / DML / multi-statement
- 视情况提取表引用 / 字段引用做审计增强

## 第三阶段：增加成本治理
后续可增加：
- 大表黑名单
- 无 where 风险标记
- select * 风险提示
- 估算成本与 explain 机制（按数据库能力决定）

---

# 五、推荐优先级

## P0
1. 先补 SQL 执行审计
2. 先补 datasource / timeout / truncated 指标

## P1
3. 引入 JSqlParser 评估 PoC
4. 做 AST 级 SELECT 校验

## P2
5. 做成本保护与风险分级
6. 做 explain / 规则引擎 / 白名单扩展

---

# 六、结论

当前 SQL 安全边界足够支撑原型和开发期，但如果要成为更完善的 DataAgent：

> **必须从正则级防护，逐步升级到 AST 级校验 + 审计 + 成本治理。**
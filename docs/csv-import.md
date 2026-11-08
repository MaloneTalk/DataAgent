# CSV 导入

CSV 是托管数据来源，底层使用 MySQL。一个 CSV 分析空间包含多张导入表，沿用现有语义层、会话数据源选择及角色表权限。

## 启用

1. 新安装执行 `sql/data_source.sql`；已有环境执行 `sql/migration.sql`。脚本兼容 MySQL 5.7，来源类型默认 DATABASE。已启用 CSV 的环境升级前先停止旧版后端，再执行迁移并启动新版，避免旧 worker 绕过批次隔离。
2. **使用 CSV 前必须新建一个独立的 MySQL 数据库**（例如 `data_agent_import`），后端不会自动创建。不要使用 DataAgent 元数据库 `data_agent`，也不要复用已有业务数据库；CSV 的读连接和写连接都必须指向这个新库。创建两个专用账号，将下面的主机和密码替换为实际部署值，不要使用示例密码。

```sql
CREATE DATABASE IF NOT EXISTS data_agent_import CHARACTER SET utf8mb4;
CREATE USER 'csv_reader'@'%' IDENTIFIED BY '<read-password>';
CREATE USER 'csv_writer'@'%' IDENTIFIED BY '<write-password>';
GRANT SELECT ON data_agent_import.* TO 'csv_reader'@'%';
GRANT CREATE, INSERT, DROP ON data_agent_import.* TO 'csv_writer'@'%';
```

3. 在 DataAgent 元数据库（通常为 `data_agent`）登记一个托管数据源，连接地址指向上一步新建的导入库，且应能从后端访问。`database_name` 必须与 URL 中的实际数据库一致。

```sql
INSERT INTO datasource
    (name, type, source_type, database_name, connection_url, username, password, status, is_deleted)
VALUES
    ('CSV 分析空间', 'mysql', 'CSV', 'data_agent_import',
     'jdbc:mysql://mysql:3306/data_agent_import?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai',
     'csv_reader', '<read-password>', 'ACTIVE', 0);
SELECT LAST_INSERT_ID();
```

4. 配置后端并重启：

```dotenv
CSV_DATASOURCE_ID=<上一步返回的ID>
CSV_WRITER_URL=jdbc:mysql://mysql:3306/data_agent_import
CSV_WRITER_USERNAME=csv_writer
CSV_WRITER_PASSWORD=<write-password>
```

本地运行可以设置 `CSV_IMPORT_DIRECTORY` 指向持久目录。Docker Compose 已将上传目录挂载到独立 volume；不要在任务排队期间清空该目录。默认 ID 为 0，不运行导入后台任务。多个后端实例运行导入时，必须共享同一个上传目录及元数据库。

5. 为需要导入的角色授予 `data:datasource:read`、`data:csv:import` 和 `semantic:table:sync`；删除另外需要 `data:csv:delete`。普通分析用户仍需配置数据表权限，上传不会自动扩大角色授权。

## 使用

进入「数据源管理 → CSV 分析空间 → 导入 CSV」。选择文件与解析选项，预览并确认字段类型后提交。导入就绪后可进入现有表语义页补充说明、业务域、数据粒度和关联关系，再选择 CSV 数据源进行分析。

- 支持 UTF-8/BOM、GB18030，以及逗号、分号、Tab 分隔。
- 支持带引号的逗号、换行和双引号转义。
- 单文件最多 50 MiB、10 万条记录、100 列；单元格最多 16 KiB（UTF-8）。超过 multipart 文件或请求大小限制时返回 HTTP 413（`PAYLOAD_TOO_LARGE`）。
- 前 1,000 条数据用于类型推断，最多展示 20 条。正式导入全量校验；错误不会被跳过或截断。
- 前导零编号、长编号优先作为文本；金额使用 DECIMAL。日期支持 `yyyy-MM-dd`，时间支持 `yyyy-MM-dd HH:mm:ss` 或以 T 分隔的同等格式，精确到秒。
- 空单元格默认 NULL，可选择保留空字符串；字符串空格及字面量 NULL 保留。
- 重名、空表头按列序号映射；实际表名和列名由服务端生成，业务名称保存到注释和语义描述中。

## 状态与恢复

```text
PREVIEWED → QUEUED → IMPORTING → DATA_READY → SYNCING → READY
写入失败 → CLEANING → IMPORT_FAILED（可调整类型并重试）
清理失败 → CLEANUP_FAILED（后台重试清理）
同步失败 → SYNC_FAILED（只重试同步，不重复导入）
删除 → DELETING → 逻辑删除；失败为 DELETE_FAILED（手动重试）
```

默认两个导入并发，队列保存在业务库，重启后继续领取任务。连续十分钟未更新的写入任务会被回收并清理；这只是恢复阈值，不代表旧 worker 已停止。每次重新导入使用独立物理表，执行状态更新绑定批次标识，避免旧 worker 恢复后影响新任务。同步中断保留完整物理数据，重试只更新执行批次及提交人；后台按本次提交人的权限执行，管理员可接管原创建人已失去权限的任务，创建人审计信息保持不变。首版不支持断点续传、追加和覆盖。

未确认和写入失败的上传文件保留 24 小时；已成功发布的原文件删除。后台定时补偿文件和失败表清理，并回收失效批次留下的、无注册记录引用的物理表；`csv_t_` 加 32 位十六进制后缀是系统保留的表名空间，请勿手工创建同名格式的表。导入按批次提交数据库事务，失败后通过删除该次物理表补偿；导入及删除不依赖跨库事务，语义元数据与 READY 状态在业务库事务中一起提交。

只有 READY 表能被发现、查询和导出，这个限制同样适用于超级管理员。删除会阻止新查询，清理物理表、语义元数据和权限；存在关联关系或指标引用时需先处理引用。指标表达式使用 SQL 解析器识别真实表引用，字符串字面量和注释不计入血缘；未限定的列或通配符、无法识别的表名、子查询和不支持的表达式均保守阻止删除，请先调整该指标口径。

托管数据源的连接不能通过普通数据源接口修改或删除。停用数据源前应先完成导入和清理任务；部署时请保持分析账号只读，写入账号只授权导入库。

## API

| 方法与路径 | 用途 |
| --- | --- |
| POST /api/csv-imports/preview | Multipart 文件与 encoding/delimiter/header/emptyAsNull，默认 UTF-8、逗号、有表头、空值转 NULL |
| PUT /api/csv-imports/{id}/preview | 调整解析选项并重新预览 |
| POST /api/csv-imports/{id}/confirm | 提交 displayName、description 和按顺序排列的 columns，返回 HTTP 202 |
| GET /api/csv-imports/{id} | 状态查询 |
| GET /api/csv-imports?page=1&pageSize=10 | 按可见范围分页 |
| POST /api/csv-imports/{id}/sync | 重试语义同步，返回 HTTP 202 |
| DELETE /api/csv-imports/{id} | 删除上传或托管表 |

重复确认（包括并发首次确认）返回同一已提交任务，复用同一执行批次和物理表。查询不会暴露服务器文件路径或写入凭据。记录仅对创建人、超级管理员，以及拥有对应表授权的用户可见。

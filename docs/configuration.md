# 配置

本文汇总 Data Agent 的各项配置：元数据库、LLM 提供商、应用参数、查询数据源、Skill 加载源、MCP Server 注册。所有配置键均可在 `application.properties` 中写死，也可用**环境变量**覆盖（推荐用于密钥与部署差异）。

## 1. 元数据库

后端自身使用 MySQL 作为元数据库。

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/data_agent?...` | 支持 `${DB_URL:...}` 环境变量覆盖 |
| `spring.datasource.username` | `root` | 支持 `${DB_USERNAME:...}` |
| `spring.datasource.password` | `root` | 支持 `${DB_PASSWORD:...}` |
| `spring.datasource.driver-class-name` | `com.mysql.cj.jdbc.Driver` | MySQL 驱动 |

新库建表脚本见 `sql/data_source.sql`。已有数据库升级时，需备份数据库并在启动新版后端前
执行统一迁移文件 `sql/migration.sql`，其中包含旧库兼容、主键关联升级、指标业务属性扩展、回答溯源表、角色功能权限、会话归属唯一约束及会话数据源范围。迁移前按[权限升级说明](permissions.md#升级现有数据库)检查会话归属冲突。
已经完成主键关联升级的旧库也需执行，以补齐计算方式、结果类型、单位、倍率、币种及回答溯源表；新增字段逐列检查是否存在，回答溯源表按需创建，可重复执行。
数据源升级还会补齐 `updater_id` 和 `is_deleted`。删除数据源改为逻辑删除，保留原记录及关联元数据；列表和会话数据源解析会排除已删除的数据源。`session_datasource` 升级为多数据源范围并补齐审计字段，工具调用会重新校验数据源权限。

指标属性的配置规则与接口示例见 [语义层](semantic-layer.md#5-指标业务属性)。

## 2. LLM 提供商

配置前缀：`io.github.malonetalk.model`。

| 配置键 | 说明 | 示例 |
| --- | --- | --- |
| `provider` | 提供商标识 | `openai` / `ollama` / `dashscope` / `anthropic` |
| `name` | 模型名 | `gpt-4o-mini` / `deepseek-chat` / `qwen-plus` |
| `base-url` | API 基址 | `https://api.openai.com/v1` |
| `api-key` | 密钥（从环境变量 `IO_GITHUB_MALONETALK_MODEL_API_KEY` 注入，勿写死） | `sk-...` |
| `thinking-enabled` | DashScope 模型是否开启思考能力，默认 `true`；其他 provider 当前会忽略该配置 | `true` / `false` |

对应环境变量写法（Spring 把点号转为下划线大写）：

```bash
export IO_GITHUB_MALONETALK_MODEL_PROVIDER="openai"
export IO_GITHUB_MALONETALK_MODEL_NAME="gpt-4o-mini"
export IO_GITHUB_MALONETALK_MODEL_BASE_URL="https://api.openai.com/v1"
export IO_GITHUB_MALONETALK_MODEL_API_KEY="sk-你的密钥"
export IO_GITHUB_MALONETALK_MODEL_THINKING_ENABLED="true"
```

各提供商常见取值：

- **openai**：`provider=openai`，`base-url=https://api.openai.com/v1`
- **ollama**（本地）：`provider=ollama`，`base-url=http://localhost:11434`，`api-key` 可留空
- **dashscope**（通义）：`provider=dashscope`，使用 `DashScopeChatModel`；当前实现读取 API Key、模型名和思考开关，不读取 `base-url`。需要自定义兼容端点时改用 `provider=openai`。
- **anthropic**：`provider=anthropic`，`base-url=https://api.anthropic.com`

> 切换底座模型只改这几项即可，已沉淀的语义层与指标口径等知识不受影响，可独立于模型底座维护。

默认值需区分启动方式：`application.properties` 与 `docker/.env.example` 的模型示例为 `openai` / `deepseek-v4-flash` / `https://api.deepseek.com`；不提供 `.env` 时，Compose 的回退值为 `dashscope` / `qwen3-max`，思考开关为 `false`。部署时请显式配置模型参数。

## 3. 应用参数

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 后端端口 |
| `spring.application.name` | `data-agent-management` | 应用名 |
| `spring.config.import` | `classpath:skill.properties` | 引入 Skill 配置 |

## 4. 查询数据源

「数据源管理」中配置的是 **Agent 连接并执行 SQL 数据分析的目标数据库**（你的业务库），与后端自身使用的元数据库（MySQL `data_agent`，见上文第 1 节）是两回事：元数据库由环境变量 `DB_URL` 指定，目标数据源在页面或 API 中配置。当前支持：

| 类型 `type` | 驱动 | JDBC 前缀 | 默认端口 | Maven 坐标 |
| --- | --- | --- | --- | --- |
| `mysql` | `com.mysql.cj.jdbc.Driver` | `jdbc:mysql://` | 3306 | `com.mysql:mysql-connector-j`（已内置） |
| `postgresql` | `org.postgresql.Driver` | `jdbc:postgresql://` | 5432 | `org.postgresql:postgresql` |
| `oracle` | `oracle.jdbc.OracleDriver` | `jdbc:oracle:thin:@` | 1521 | `com.oracle.database.jdbc:ojdbc11` |
| `clickhouse` | `com.clickhouse.jdbc.ClickHouseDriver` | `jdbc:clickhouse://` | 8123 | `com.clickhouse:clickhouse-jdbc` |
| `sqlserver` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` | `jdbc:sqlserver://` | 1433 | `com.microsoft.sqlserver:mssql-jdbc` |
| `dameng` | `dm.jdbc.driver.DmDriver` | `jdbc:dm://` | 5236 | `com.dameng:DmJdbcDriver18`（不在中央仓库，需从达梦官网获取后本地安装） |
| `oceanbase` | `com.oceanbase.jdbc.Driver` | `jdbc:oceanbase://` | 2881 | `com.oceanbase:oceanbase-client` |
| `sqlite` | `org.sqlite.JDBC` | `jdbc:sqlite:` | 无（文件路径） | `org.xerial:sqlite-jdbc` |

> **⚠️ 驱动依赖**：后端**默认仅内置 MySQL 驱动**。使用上表其他数据库前，需先在 `data-agent-backend/pom.xml` 中添加对应驱动依赖并重新构建、启动后端，否则新增/连接数据源会报「未找到数据库驱动」。其中**达梦驱动不在 Maven 中央仓库**，需从达梦官网下载 jar 后执行 `mvn install:install-file` 安装到本地仓库。

以 PostgreSQL 为例，在 `data-agent-backend/pom.xml` 的 `<dependencies>` 中加入：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

> **SQLite 特殊说明**：SQLite 是本地文件数据库，没有主机/端口概念。新增时无需填写主机/端口/数据库名，直接在「连接URL」中填写形如 `jdbc:sqlite:/path/to/db` 的文件路径即可。

> **表格导出限制**：`export_table` 会把 SELECT 结果导出为 CSV，CSV 内容保存在元数据库中，单次最多 10000 行。当前大批量导出的流式读取按 MySQL 路径处理；PostgreSQL 虽可接入查询，但大批量导出前需在实现中补 cursor/事务设置（例如关闭 auto-commit），否则驱动可能一次性拉取结果集。

CSV 按 JDBC 类型转换：布尔、数值、字符和日期时间类型使用驱动提供的文本表示；`BINARY`、`VARBINARY`、`LONGVARBINARY` 和 `BLOB` 读取实际字节，输出为带 `0x` 前缀的小写十六进制（例如 `0x00ff`，空字节串为 `0x`）；`CLOB`、`NCLOB` 读取完整文本。SQL `NULL` 输出为空单元格。其他类型（如 `ARRAY`、`STRUCT`、`SQLXML`、`OTHER`）遇到数据时会终止导出并报错，需先在查询中显式转换为支持的标量类型。所有文本继续按 CSV 规则转义，并对可能触发电子表格公式的内容添加单引号前缀。

> 上述列表之外的 JDBC 数据库，需扩展 `DataSourceType` 枚举、添加驱动，并验证元数据、SQL 授权与分页行为（见 [contributing.md](contributing.md)）。

> 连接信息（host/port/database/username/password）保存在元数据库中，属敏感信息，请妥善管理元数据库访问权限。

### 数据源接口与会话范围

- 创建的数据源默认 `INACTIVE`。聊天页可选择多个数据源；不选择时使用当前用户所有可访问的启用数据源。数据源范围会按消息更新，工具通过 `datasource_id` 指定实际查询目标。
- 更新时 `name`、`type` 必填；可选字段未传或为 `null` 时保留原值，可选字符串可用 `""` 清空，密码传空字符串仍保留原密码。更新、删除会清理该源的连接池缓存。
- 删除为逻辑删除，保留语义及会话数据源范围。已选择的数据源被删除或撤权后，后续工具调用会拒绝；可在下一条消息中调整数据源选择。
- 数据源创建、更新、删除与启停接口返回 `data: { "value": true }`；列表与摘要选项的 `data` 仍是数组，字段查询仍是表名到列名数组的映射。读取接口不返回密码。
- `GET /api/datasource/options` 供聊天、表语义、逻辑外键与指标页面共享，按表授权过滤，只含 `id`、`name`、`type`、`status`。

普通查询最多返回 200 行、超时 30 秒；导出最多 10000 行、超时 120 秒。JDBC 驱动解决连接问题，实际 SQL 还需符合目标库方言及当前授权解析器支持范围，详见 [架构说明](architecture.md#7-agent-内置工具) 和 [权限规则](permissions.md#后端执行规则)。

## 5. Skill 加载源

配置前缀：`io.github.malonetalk.skill`。支持四种来源，可同时使用：

```properties
# 文件系统来源
io.github.malonetalk.skill.filesystem[0].path=./skills
io.github.malonetalk.skill.filesystem[0].source=local-fs
io.github.malonetalk.skill.filesystem[0].writeable=true

# Git 来源（自动同步）
io.github.malonetalk.skill.git[0].url=https://github.com/your-org/your-skills.git
io.github.malonetalk.skill.git[0].branch=main
io.github.malonetalk.skill.git[0].local-path=/tmp/skills-cache
io.github.malonetalk.skill.git[0].source=git-repo

# Nacos 来源（需显式指定 skillNames）
io.github.malonetalk.skill.nacos[0].server-addr=127.0.0.1:8848
io.github.malonetalk.skill.nacos[0].skill-names[0]=data-query

# classpath 来源
io.github.malonetalk.skill.classpath[0].resource-path=skills
io.github.malonetalk.skill.classpath[0].source=classpath-skills
```

仓库内置示例：`skills/data-query/SKILL.md`。

文件系统路径相对于后端进程的工作目录解析。进入 `data-agent-backend/` 启动时，需把路径设为 `../skills`：

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--io.github.malonetalk.skill.filesystem[0].path=../skills"
```

Docker 后端工作目录为 `/app`，镜像已复制 `/app/skills`，默认 `./skills` 可直接使用。Skill 在 `AgentService` 初始化时加载并注册；新增或修改本地文件后重启后端，镜像内文件更新还需重新构建。Git 的 `auto-sync` 控制仓库同步，不代表应用会自动重新注册 Skill。

## 6. MCP Server 配置管理

通过 API `/api/mcp-server` 保存外部 MCP Server 配置，支持两种形态：

- **STDIO**：填 `command`、`args`、`env`。
- **HTTP/SSE**：填 `url`、`headers`、`queryParams`。

`PUT /api/mcp-server/{id}/enable` 与 `/disable` 仅更新配置状态。当前 Agent 尚未连接这些 Server 或注册它们的工具，详见 [architecture.md](architecture.md#4-mcp-配置管理)。读取、编辑与启停分别要求 `sys:mcp:read`、`sys:mcp:update`、`sys:mcp:set-status`。

## 7. 认证配置（JWT + 管理员）

前缀：`jwt`、`admin`。所有值均从环境变量注入，`application.properties` 中不存任何密钥明文。

| 配置键 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jwt.secret` | `JWT_SECRET` | 空 | JWT 签名密钥。**生产环境必须设置且长度 ≥ 32 字节**；留空则使用内存随机密钥（仅开发环境可用，每次重启 token 全部失效）。 |
| `jwt.expiration-hours` | `JWT_EXPIRATION_HOURS` | `24` | Token 过期时间（小时）。 |
| `admin.init-password` | `ADMIN_INIT_PASSWORD` | 空 | 管理员初始密码。**不设则启动失败（fail-closed）**。仅在 `sys_user` 表为空时首次自动创建 `admin` 用户，表已有数据后不再生效。 |

对应环境变量写法：

```bash
export JWT_SECRET="至少32字节的随机字符串建议用openssl生成"
export JWT_EXPIRATION_HOURS="24"
export ADMIN_INIT_PASSWORD="你的管理员密码"
```

> ⚠️ **不要把这几个值写进 `application.properties` 并提交到仓库**——尤其是 `admin.init-password` 和 `jwt.secret`，它们与 LLM 的 `api-key` 一样属于敏感信息。`application.properties` 中仅声明占位符 `${ADMIN_INIT_PASSWORD:}`、`${JWT_SECRET:}`，实际取值由环境变量注入。

### 工作原理

- 后端启动时，`AdminBootstrapRunner` 检查 `sys_user` 表是否为空；若为空，用 `admin.init-password` 创建首个超级管理员（用户名固定为 `admin`，`displayName` 为「超级管理员」，`role_id=0`，`is_super_admin=1`）。
- 登录接口 `POST /api/auth/login` 接受 `{ username, password }`，返回 `{ token, user }`。前端将 token 存入 `localStorage`，后续请求通过 `Authorization: Bearer <token>` 携带。
- `AuthInterceptor` 拦截除 `/api/auth/login` 外的所有接口（含 SSE 流式端点），校验 token 签名与时效。
- 本人资料、修改本人密码等基础接口使用 `@LoginRequired`；业务接口使用 `@RequirePermission`，按当前角色的权限点检查，未声明策略的接口默认拒绝。超级管理员由 `is_super_admin=1` 标记，角色 ID 不代表管理员身份。
- 「系统管理」中可配置角色功能权限、表白名单与隐藏列黑名单，再为用户分配一个角色。Agent 语义工具、SQL 查询、导出与相关管理操作均执行后端权限校验；权限变更在后续请求和敏感工具调用时重新读取，不写入 JWT。`GET /api/auth/me` 返回当前用户、角色、超级管理员标记与有效权限编码，前端据此控制路由和按钮。完整授权规则见 [permissions.md](permissions.md)。

### 密码哈希与旧数据迁移

- 若从更早的 PBKDF2 版本升级、库里仍是 `pbkdf2$...` 旧哈希，这些用户无法登录（BCrypt 校验不识别旧格式），需要逐个重置密码。
- 迁移使用内置自检工具 `SysUserPasswordMigration`（`src/test/java/io/github/malonetalk/`）：分页列出全部用户，逐个提示重置**非 BCrypt** 的密码，已是 BCrypt 的自动跳过。

```bash
# 元数据库连接沿用 DB_URL / DB_USERNAME / DB_PASSWORD
SELF_CHECK=true mvn -f data-agent-backend/pom.xml clean test -Dtest=SysUserPasswordMigration -DforkCount=0
```

## 8. 安全提示

- 不要把 `IO_GITHUB_MALONETALK_MODEL_API_KEY`、`JWT_SECRET`、`ADMIN_INIT_PASSWORD` 写入仓库。
- 生产环境必须设置长度不少于 32 字节的 `JWT_SECRET`；留空只适合本地开发。
- 目标数据源的连接信息会保存在元数据库中，请限制元数据库账号、网络与备份访问权限。

## 9. 可观测性（OpenTelemetry）

后端已接入 OpenTelemetry，trace / metrics / logs **默认只写终端，导出开关默认关闭**。导出使用标准 OTLP/HTTP，可对接任意 OTel 后端（Jaeger、Tempo、SigNoz、Langfuse 等），Grafana LGTM 仅为本地示例。完整说明（覆盖范围、接入示例、排障）见 [observability.md](observability.md)。

| 配置键 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `management.tracing.sampling.probability` | - | `1.0` | 采样率（0.0–1.0） |
| `management.tracing.propagation.type` | - | `w3c` | 跨服务传播格式 |
| `management.tracing.export.otlp.enabled` | `TRACING_OTLP_EXPORT_ENABLED` | `false` | 是否导出 trace |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP trace 地址 |
| `management.otlp.metrics.export.enabled` | `METRICS_OTLP_EXPORT_ENABLED` | `false` | 是否导出 metrics |
| `management.otlp.metrics.export.url` | `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` | `http://localhost:4318/v1/metrics` | OTLP metrics 地址 |
| `management.logging.export.otlp.enabled` | `LOGS_OTLP_EXPORT_ENABLED` | `false` | 是否导出日志 |
| `management.opentelemetry.logging.export.otlp.endpoint` | `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` | `http://localhost:4318/v1/logs` | OTLP logs 地址 |

> 仅查看终端日志时无需配置任何项。接入 Collector 时，把对应 `*_ENABLED` 设为 `true` 并指向 OTLP 地址即可；三种信号可独立开启。

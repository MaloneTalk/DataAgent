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

建表脚本见 `sql/data_source.sql`。

## 2. LLM 提供商

配置前缀：`io.github.malonetalk.model`。

| 配置键 | 说明 | 示例 |
| --- | --- | --- |
| `provider` | 提供商标识 | `openai` / `ollama` / `dashscope` / `anthropic` |
| `name` | 模型名 | `gpt-4o-mini` / `deepseek-chat` / `qwen-plus` |
| `base-url` | API 基址 | `https://api.openai.com/v1` |
| `api-key` | 密钥（从环境变量 `IO_GITHUB_MALONETALK_MODEL_API_KEY` 注入，勿写死） | `sk-...` |

对应环境变量写法（Spring 把点号转为下划线大写）：

```bash
export IO_GITHUB_MALONETALK_MODEL_PROVIDER="openai"
export IO_GITHUB_MALONETALK_MODEL_NAME="gpt-4o-mini"
export IO_GITHUB_MALONETALK_MODEL_BASE_URL="https://api.openai.com/v1"
export IO_GITHUB_MALONETALK_MODEL_API_KEY="sk-你的密钥"
```

各提供商常见取值：

- **openai**：`provider=openai`，`base-url=https://api.openai.com/v1`
- **ollama**（本地）：`provider=ollama`，`base-url=http://localhost:11434`，`api-key` 可留空
- **dashscope**（通义）：`provider=dashscope`，`base-url=https://dashscope.aliyuncs.com/compatible-mode/v1`
- **anthropic**：`provider=anthropic`，`base-url=https://api.anthropic.com`

> 切换底座模型只改这几项即可，已沉淀的语义层与指标口径等知识不受影响，可独立于模型底座维护。

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

> 上述列表之外的其他 JDBC 兼容数据库，可通过扩展 `DataSourceType` 枚举接入（见 [contributing.md](contributing.md)）。

> 连接信息（host/port/database/username/password）保存在元数据库中，属敏感信息，请妥善管理元数据库访问权限。

## 5. Skill 加载源

配置前缀：`io.github.malonetalk.skill`。支持四种来源，可同时使用：

```properties
# 文件系统来源
io.github.malonetalk.skill.filesystem[0].path=skills
io.github.malonetalk.skill.filesystem[0].source=data-query
io.github.malonetalk.skill.filesystem[0].writeable=true

# Git 来源（自动同步）
io.github.malonetalk.skill.git[0].url=https://github.com/your-org/your-skills.git
io.github.malonetalk.skill.git[0].branch=main
io.github.malonetalk.skill.git[0].local-path=/tmp/skills-cache
io.github.malonetalk.skill.git[0].source=data-query

# Nacos 来源（需显式指定 skillNames）
io.github.malonetalk.skill.nacos[0].server-addr=127.0.0.1:8848
io.github.malonetalk.skill.nacos[0].skill-names[0]=data-query

# classpath 来源
io.github.malonetalk.skill.classpath[0].resource-path=skills/data-query
io.github.malonetalk.skill.classpath[0].source=data-query
```

仓库内置示例：`skills/data-query/SKILL.md`。

## 6. MCP Server 注册

通过 API `/api/mcp-server` 注册外部 MCP Server，把外部工具纳入 Agent。两种形态：

- **STDIO**：填 `command`、`args`、`env`。
- **HTTP/SSE**：填 `url`、`headers`、`queryParams`。

注册后调用 `PUT /api/mcp-server/{id}/enable` 启用。详见 [architecture.md](architecture.md#5-mcp-集成)。

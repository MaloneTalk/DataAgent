# 快速开始

本文带你从零把 Data Agent 跑起来，并在前端用自然语言提第一个问题。预计耗时 10–20 分钟。

## 方式一：Docker 启动（推荐）

最快的方式，自动搭建 MySQL、后端、前端。

### 前置要求

- Docker 20.10+ 与 Docker Compose v2.0+
- 支持 Linux / Windows / macOS

### 环境变量配置

启动前需要配置环境变量, 将`docker/.env.example`重命名为`docker/.env`：

```bash
cp docker/.env.example docker/.env
```

然后编辑 `docker/.env`，参考以下说明：

**必填项：**
```bash
# AI 模型配置（必须配置，否则 AI 功能无法使用）
IO_GITHUB_MALONETALK_MODEL_API_KEY={你的密钥}
IO_GITHUB_MALONETALK_MODEL_PROVIDER=openai
IO_GITHUB_MALONETALK_MODEL_NAME=deepseek-v4-flash
IO_GITHUB_MALONETALK_MODEL_BASE_URL=https://api.deepseek.com
IO_GITHUB_MALONETALK_MODEL_THINKING_ENABLED=true
```

**可选项（有默认值，生产环境建议修改）：**
```bash
# 数据库密码（默认：root）
DB_PASSWORD=

# JWT 密钥（默认：空，生产环境必须设置且 >= 32 字节）
JWT_SECRET=

# 管理员初始密码（默认：admin）
ADMIN_INIT_PASSWORD=
```

### 启动

```bash
cd docker
docker-compose up -d --build
```

首次启动会自动：
- 构建后端/前端镜像
- 下载 MySQL 镜像
- 执行 `sql/` 目录初始化数据库
- 启动所有服务

修改配置后重启：`docker-compose restart`

### 访问

- **前端**: http://localhost:3000
- **后端**: http://localhost:8080
- **管理员账号**: `admin` / `admin`（或你设置的 `ADMIN_INIT_PASSWORD`）

---

## 方式二：手动搭建

适合开发调试或不想用 Docker 的场景。

#### 1. 前置依赖

| 依赖 | 版本要求 | 说明 |
| --- | --- | --- |
| JDK | 17 或更高（推荐 21） | 后端基于 Spring Boot 4 |
| Maven | 3.9+ | 构建与运行后端 |
| Node.js | 18+ | 前端运行环境 |
| pnpm | 8+ | 前端包管理 |
| MySQL | 8+ | 元数据库（存语义层/数据源/会话等） |

### 2. 准备元数据库

Data Agent 需要一张 MySQL 元数据库来存放语义层、数据源、会话等信息。

```bash
# 创建数据库（字符集务必为 utf8mb4）
mysql -u root -p -e "CREATE DATABASE data_agent CHARACTER SET utf8mb4;"

# 导入表结构
mysql -u root -p data_agent < sql/data_source.sql
```

> `sql/data_source.sql` 包含全部元数据库初始化表结构，导入这一份即可。

### 3. 配置并启动后端

后端默认端口 `8080`，应用名 `data-agent-management`。启动前需要告诉它：元数据库在哪、用哪个 LLM。

### 3.1 元数据库（环境变量，推荐）

```bash
export DB_URL="jdbc:mysql://localhost:3306/data_agent?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
export DB_USERNAME="root"
export DB_PASSWORD="你的密码"
```

### 3.2 LLM 提供商（环境变量，推荐）

配置键前缀为 `io.github.malonetalk.model`，支持 `provider`：`openai` / `ollama` / `dashscope` / `anthropic`。

```bash
# 以 OpenAI 兼容端点为例
export IO_GITHUB_MALONETALK_MODEL_PROVIDER="openai"
export IO_GITHUB_MALONETALK_MODEL_NAME="gpt-4o-mini"
export IO_GITHUB_MALONETALK_MODEL_BASE_URL="https://api.openai.com/v1"
export IO_GITHUB_MALONETALK_MODEL_API_KEY="sk-你的密钥"

# DashScope 模型是否开启思考能力，默认 true；其他 provider 当前会忽略
export IO_GITHUB_MALONETALK_MODEL_THINKING_ENABLED="true"
```

> ⚠️ **不要把 API Key 写进 `application.properties` 并提交到仓库。** 密钥现已改为从环境变量 `IO_GITHUB_MALONETALK_MODEL_API_KEY` 注入。详见 [configuration.md](configuration.md#安全提示) 。

### 3.3 认证配置（JWT + 管理员）

后端集成了 JWT 登录机制，首次启动前需要设置以下环境变量：

```bash
# JWT 密钥，生产环境必须设置且长度 ≥ 32 字节；留空则使用内存随机密钥（仅开发可用，重启后所有 token 失效）
export JWT_SECRET="至少32字节的随机字符串"

# JWT 过期时间（小时），默认 24
export JWT_EXPIRATION_HOURS="24"

# 管理员初始密码，sys_user 表为空时自动创建 admin 账号；不设则启动失败
export ADMIN_INIT_PASSWORD="你的管理员密码"
```

> ⚠️ **`ADMIN_INIT_PASSWORD` 不设会启动失败**（fail-closed）。它仅在看 `sys_user` 表为空时首次生效，已有用户后不再使用。`JWT_SECRET` 留空的后果是每次重启所有已登录用户被迫重新登录——开发环境可接受，生产环境必须设。

> 各配置键的完整说明见 [configuration.md](configuration.md#7-认证配置)。

### 3.4 启动

```bash
cd data-agent-backend
mvn spring-boot:run
```

看到日志中嵌入式容器启动在 `8080` 即成功。

### 4. 启动前端

```bash
cd data-agent-frontend
pnpm install
pnpm dev
```

前端默认运行在 http://localhost:3000 ，开发代理已把 `/api` 转发到 `http://localhost:8080`（见 `vite.config.ts`）。

## 第一次提问

> **先分清两类数据库**：「数据源管理」里配置的是 **Agent 要连接、执行 SQL 数据分析的目标数据库**（你的业务库），不是第 2 步准备的元数据库 `data_agent`（后端存放语义层/数据源配置/会话的库）。元数据库在后端启动时通过环境变量 `DB_URL` 指定，不在页面上配置。

1. 打开 http://localhost:3000 。
2. 先在「数据源管理」中新增一个你要查的业务库（支持 MySQL / PostgreSQL / Oracle / ClickHouse / SQL Server / 达梦 / OceanBase / SQLite，类型以下拉列表为准）。若该库不是 MySQL，请先确认后端已内置其驱动（见 [常见问题](#6-常见问题) 第一条）。
3. 在「语义管理」中同步物理表，再把相关表/列映射成业务语言；指标口径也在这里维护（可选，但能显著提升准确率）。
4. 进入聊天界面，输入类似：*"上个月各区域销售额是多少？"*，观察流式回答与生成的 SQL。

> 初始的 `admin` 用户会绑定管理员角色（`role_id=1`），可访问所有标记了 `@AdminOnly` 的管理接口。如需多人使用，可在「系统管理」中创建用户、配置角色的表级白名单与列级黑名单、再将用户绑定到角色。当前表/列权限仍主要用于管理侧配置，Agent 推理链路的表/列过滤还在后续完善中。

> 如果回答不准，多半是语义层/指标口径没配好，或数据源尚未接入。参见 [semantic-layer.md](semantic-layer.md) 与 [configuration.md](configuration.md)。

## 常见问题

- **新增数据源连接失败，提示「未找到数据库驱动」**：后端默认仅内置 MySQL 驱动。请在 `data-agent-backend/pom.xml` 中添加你所用数据库的 JDBC 驱动依赖（坐标见 [configuration.md](configuration.md#4-查询数据源)），重新构建并启动后端后再试。
- **启动后端报数据源连接失败**：检查 `DB_URL` 中的库名、账号密码，以及 MySQL 是否允许该连接方式。
- **前端白屏 / 接口 404**：确认后端已起在 8080，且前端的 `/api` 代理指向正确。
- **LLM 调用报错 401**：检查 `io.github.malonetalk.model.api-key` / `base-url` 是否与所选 `provider` 匹配。

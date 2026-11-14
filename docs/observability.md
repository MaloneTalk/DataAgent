# 可观测性（OpenTelemetry）

Data Agent 后端基于 **OpenTelemetry（OTel）** 统一接入 traces / metrics / logs 三类信号，并保证一次对话从 HTTP 请求、Agent 推理、LLM 调用、工具执行到 SQL 查询都落在同一条 trace 上。导出走标准 **OTLP/HTTP**，**不绑定任何厂商**：可对接 Jaeger、Tempo、SigNoz、Uptrace、Honeycomb、Langfuse、Arize Phoenix 等任意 OTel 兼容后端。本文以 Grafana LGTM 作为本地示例，说明接入范围、配置开关与常见问题。

## 1. 已接入的信号

| 信号 | 实现 | 说明 |
| --- | --- | --- |
| Trace | Micrometer Tracing + `micrometer-tracing-bridge-otel` | HTTP server span、agentscope span、JDBC span |
| Metrics | Micrometer + `micrometer-registry-otlp` | JVM、HTTP、Hikari 连接池等指标 |
| Logs | `opentelemetry-logback-appender-1.0` | 应用日志经 OTLP 导出，带 trace/span 关联 |

一次 SSE 对话的典型链路：

```
HTTP POST /api/agent/chat/stream
  └─ invoke_agent DataAgent
       ├─ chat <model>                 # LLM 调用
       ├─ execute_tool get_tables      # 工具调用
       └─ execute_tool execute_sql
            └─ SELECT ...              # db.* span（MyBatis / 动态数据源）
```

### 具体覆盖范围

- **HTTP**：由 Spring Boot 自动为每个请求创建 server span，写入 MDC 的 `traceId`/`spanId`，响应头回显 `X-Trace-Id`。
- **Agent / LLM / Tool**：通过 `TracerRegistry.register(TelemetryTracer)` 交给 agentscope，自动产生 `invoke_agent`、`chat`、`execute_tool`、`format` span。
- **JDBC**：主库（MyBatis）与动态数据源（Agent 查询的业务库）均通过 `JdbcTelemetry` 包装，SQL 调用记录为 `db.*` span；SQL 文本默认做字面量清洗。
- **日志**：Logback 新增 `OTEL` appender，按配置决定是否导出；日志记录携带 `trace_id`/`span_id`。
- **指标**：Hikari 连接池指标通过 `OpenTelemetryDataSource.unwrap` 仍然可用（`hikaricp.*`），同时也有通用的 `jdbc.connections.*`。

## 2. 配置开关

所有开关均可写在 `application.properties`，也可用环境变量覆盖。**默认全部关闭导出，只输出终端日志**。

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

三种信号可以独立开启。只想要链路追踪而不导出日志/指标时，仅设置 `TRACING_OTLP_EXPORT_ENABLED=true` 即可。

### traceId 与响应头

- 日志 pattern 已包含 `%X{traceId}`（见 `logback-spring.xml`）。
- 响应头 `X-Trace-Id` 会回显当前 traceId（32 位十六进制）。
- 入站 `X-Trace-Id` 不再复用；跨服务统一使用 W3C `traceparent`。

## 3. 接入任意 OTLP 后端（Grafana 只是示例）

本项目只使用标准 OTLP/HTTP 导出，不依赖任何厂商 SDK。把 endpoint 指向目标后端即可；需要鉴权的后端再补充 headers。

| 后端 | 类型 | 说明 | 需要开启的信号 |
| --- | --- | --- | --- |
| Grafana Tempo / LGTM | 通用 | 仓库自带本地示例，trace/metrics/logs | 按需 |
| Jaeger | 通用 | OTLP 原生，主要看 trace | Trace |
| SigNoz | 通用 | trace/metrics/logs | 按需 |
| Uptrace | 通用 | trace/metrics/logs | 按需 |
| Honeycomb | 通用 | trace/metrics | 按需 |
| Datadog / New Relic | 通用 | 通过各自的 OTLP intake 接入 | 按需 |
| Langfuse | LLM 可观测 | 接收 OTLP traces，适合查看 Agent/LLM 调用 | Trace |
| Arize Phoenix | LLM 可观测 | 接收 OTLP traces | Trace |

以 **Langfuse** 为例（仅需 trace，OTLP HTTP，不支持 gRPC）：

```properties
# endpoint 使用 Langfuse 的 signal-specific 地址
management.tracing.export.otlp.enabled=true
management.opentelemetry.tracing.export.otlp.endpoint=https://cloud.langfuse.com/api/public/otel/v1/traces

# Langfuse 使用 Basic Auth，需要通过 Spring Boot 属性带鉴权头
management.opentelemetry.tracing.export.otlp.headers.Authorization=Basic <base64(pk-lf-...:sk-lf-...)>
management.opentelemetry.tracing.export.otlp.headers.x-langfuse-ingestion-version=4
```

> 上面是直接写在 `application.properties` 的写法；对应的环境变量见第 2 节表格。`AUTH_STRING` 用 `echo -n "pk-lf-...:sk-lf-..." | base64` 生成；自托管或其他数据区域的 endpoint 以 Langfuse 文档为准。只需要 trace 时不要打开 metrics/logs 开关。

如果后端要求的鉴权或协议转换比较特殊，推荐在前面放一个 **OpenTelemetry Collector**：应用统一发给 Collector，由 Collector 再分发到 Jaeger / Langfuse / 云厂商，应用侧只维护一个 endpoint。

> 由于 Spring Boot 对 `Map` 类型配置的环境变量绑定不直观，鉴权头建议写在 `application.properties`，或交给 Collector 处理。

## 4. 本地示例：Grafana LGTM（可选）

仓库自带一个单容器观测栈 `docker/observability/docker-compose.yml`（Grafana LGTM：Collector + Tempo + Prometheus + Loki + Grafana），仅用于开发/演示。**它只是示例，不是唯一选择**；不起它也可以直接把 endpoint 指向其他 OTLP 后端。

```bash
# 1. 先启动主栈（创建 data-agent-network）
docker compose -f docker/docker-compose.yml up -d

# 2. 启动观测栈
docker compose -f docker/observability/docker-compose.yml up -d
```

访问：

- Grafana：http://localhost:3001（默认 `admin` / `admin`）
- OTLP gRPC：`4317`
- OTLP HTTP：`4318`

在 `docker/.env` 中开启导出（`.env.example` 已给出注释示例）：

```properties
TRACING_OTLP_EXPORT_ENABLED=true
METRICS_OTLP_EXPORT_ENABLED=true
LOGS_OTLP_EXPORT_ENABLED=true
```

Docker Compose 中三个 endpoint 已默认指向 `otel-lgtm:4318`，通常只需打开上面的开关；如果在宿主机直接运行后端，再按需设置 `OTEL_EXPORTER_OTLP_*_ENDPOINT=http://localhost:4318/...`。

然后重建并重启后端：

```bash
docker compose -f docker/docker-compose.yml up -d --build backend
```

查看方式：

- **Trace**：Grafana → Explore → Tempo，按 `service.name=data-agent-management` 查询。
- **Metrics**：Grafana → Explore → Prometheus。
- **Logs**：Grafana → Explore → Loki，按 `{service_name="data-agent-management"}` 查询；日志可与同 trace 的 span 互相跳转。

## 5. 依赖与版本

- Spring Boot `4.0.3` 管理 OTel `1.55.0`、micrometer-tracing `1.6.3`。
- OTel instrumentation 系列（reactor / logback appender / jdbc）为 `2.9.0-alpha`，该系列目前只有 alpha 版本。
- `opentelemetry-api-incubator` 固定为 `1.55.0-alpha`，必须与 SDK 版本对齐，否则日志导出会报 `NoClassDefFoundError: ExtendedAttributes`。

版本锁定理由见 `data-agent-backend/pom.xml` 中对应注释。

## 6. 常见问题

- **Docker 构建 Maven 依赖超时**：宿主机启用 Clash/Meta fake-ip 时，构建容器可能拿到 `198.18.x` 的假地址。`docker/docker-compose.yml` 已为 backend 构建设置 `network: host`；如仍失败，可启用 `docker/backend/Dockerfile` 中注释的阿里云镜像（并给 `mvn` 加 `-s /root/settings.xml`）。
- **Grafana 里没有日志**：先确认 `LOGS_OTLP_EXPORT_ENABLED=true`、后端镜像已重建，且 `docker logs data-agent-backend` 中没有 OTLP 导出异常。
- **看不到 DB span**：确认目标数据源从 backend 容器可达；SQL span 需要当前 OTel 上下文，Agent 异步链路依赖 agentscope 的 Reactor hook。
- **担心 Hikari 指标丢失**：不会。Boot 会通过 `DataSourceUnwrapper` 解包 `OpenTelemetryDataSource`，`hikaricp.*` 指标照常产生。

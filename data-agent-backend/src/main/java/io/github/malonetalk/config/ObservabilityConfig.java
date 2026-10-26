/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */
package io.github.malonetalk.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.agentscope.core.tracing.TracerRegistry;
import io.agentscope.core.tracing.telemetry.TelemetryTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 将 Spring Boot 管理的 OTel {@link Tracer} 注册进 agentscope，使 Agent/LLM/Tool span 与 HTTP
 * 请求链路共用同一个 traceId；并按配置决定是否把应用日志导出到 OTLP。
 */
@Configuration
@RequiredArgsConstructor
public class ObservabilityConfig {

    private static final String OTEL_APPENDER_NAME = "OTEL";
    private static final String LOG_EXPORT_ENABLED_PROPERTY =
            "management.logging.export.otlp.enabled";

    private final Tracer otelTracer;
    private final OpenTelemetry openTelemetry;
    private final Environment environment;

    @Bean
    JdbcTelemetry jdbcTelemetry() {
        return JdbcTelemetry.create(openTelemetry);
    }

    @PostConstruct
    void init() {
        TracerRegistry.register(TelemetryTracer.builder().tracer(otelTracer).build());
        configureLogExport();
    }

    private void configureLogExport() {
        if (environment.getProperty(LOG_EXPORT_ENABLED_PROPERTY, Boolean.class, false)) {
            OpenTelemetryAppender.install(openTelemetry);
            return;
        }
        // 未开启日志导出时移除 appender，保持默认仅终端输出，避免其缓冲日志。
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext) {
            loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(OTEL_APPENDER_NAME);
        }
    }
}

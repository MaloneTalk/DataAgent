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

import io.agentscope.core.tracing.TracerRegistry;
import io.agentscope.core.tracing.telemetry.TelemetryTracer;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * 将 Spring Boot 管理的 OTel {@link Tracer} 注册进 agentscope，使 Agent/LLM/Tool span 与 HTTP
 * 请求链路共用同一个 traceId。
 */
@Configuration
@RequiredArgsConstructor
public class ObservabilityConfig {

    private final Tracer otelTracer;

    @PostConstruct
    void registerAgentTracer() {
        TracerRegistry.register(TelemetryTracer.builder().tracer(otelTracer).build());
    }
}

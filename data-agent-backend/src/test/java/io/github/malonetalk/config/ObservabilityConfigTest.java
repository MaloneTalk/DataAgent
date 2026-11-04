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

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.tracing.TracerRegistry;
import io.agentscope.core.tracing.telemetry.TelemetryTracer;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ObservabilityConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    OpenTelemetrySdkAutoConfiguration.class,
                                    OpenTelemetryTracingAutoConfiguration.class))
                    .withUserConfiguration(ObservabilityConfig.class)
                    .withPropertyValues("management.tracing.sampling.probability=1.0");

    @Test
    void registersOtelBackedAgentTracer() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasSingleBean(Tracer.class);
                    assertThat(ctx.getBean(Tracer.class)).isNotNull();
                    assertThat(TracerRegistry.get()).isInstanceOf(TelemetryTracer.class);
                });
    }
}

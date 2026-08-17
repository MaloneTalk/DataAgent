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

import static io.github.malonetalk.common.Constants.SCHEDULE_PROPERTIES_PREFIX;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@ConfigurationProperties(prefix = SCHEDULE_PROPERTIES_PREFIX)
@Data
public class ScheduledAgentScheduleProperties {

    @Positive private int batchSize;

    private long dispatchDelayMs;

    @NotNull private Duration lockDuration;

    @Valid @NotNull private Executor executor = new Executor();

    @Data
    public static class Executor {

        @Positive private int corePoolSize;

        @Positive private int maxPoolSize;

        @Positive private int queueCapacity;
    }
}

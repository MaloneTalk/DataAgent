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
package io.github.malonetalk.agent;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.github.malonetalk.service.ToolRiskService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/** Checks tool calls using the current chat's trusted execution context. */
@RequiredArgsConstructor
final class DangerousOperationHook implements Hook {
    private final ToolCallContext context;
    private final ToolRiskService riskService;

    @Override
    public int priority() {
        // Validate after parameter-preprocessing hooks.
        return 1000;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        return Mono.defer(
                () -> {
                    if (event instanceof PreActingEvent acting) {
                        var call = acting.getToolUse();
                        riskService.requireAllowed(context, call.getName(), call.getInput());
                    }
                    return Mono.just(event);
                });
    }
}

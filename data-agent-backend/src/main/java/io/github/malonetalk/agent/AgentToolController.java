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

import io.github.malonetalk.agent.tools.AgentToolInvoker;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.interceptor.AgentAuthInterceptor;
import io.github.malonetalk.model.bo.ToolResultBo;
import io.github.malonetalk.model.bo.UserPatBo;
import io.github.malonetalk.model.converter.UserPatConverter;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.vo.AgentToolVo;
import io.github.malonetalk.model.vo.ListVo;
import io.github.malonetalk.model.vo.ToolResultVo;
import io.github.malonetalk.model.vo.UserPatVo;
import io.github.malonetalk.service.DataPermissionService;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/agent")
public class AgentToolController {

    private static final String SESSION_ID_HEADER = "X-Session-Id";
    private static final String CLIENT_AGENT_CODE = "CLIENT";
    private static final String DEFAULT_SESSION = "__default__";

    private final AgentToolInvoker invoker;
    private final DataPermissionService dataPermissions;
    private final UserContextHolder userContextHolder;
    private final UserPatConverter userPatConverter;
    private final Tracer tracer;

    @GetMapping("/health")
    public UserPatVo health(HttpServletRequest request) {
        UserPatBo pat =
                Optional.ofNullable(
                                (UserPatBo)
                                        request.getAttribute(
                                                AgentAuthInterceptor.AUTHENTICATED_PAT_ATTRIBUTE))
                        .orElseThrow(() -> BusinessException.of(ErrorCode.UNAUTHORIZED));
        return userPatConverter.toVo(pat);
    }

    @GetMapping("/tools")
    public ListVo<AgentToolVo> listTools() {
        return new ListVo<>(
                invoker.list().stream()
                        .map(
                                tool ->
                                        new AgentToolVo(
                                                tool.name(), tool.description(), tool.parameters()))
                        .toList());
    }

    @PostMapping("/tools/{name}")
    public ToolResultVo callTool(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> input,
            HttpServletRequest request) {
        var user = userContextHolder.checkAndGet();
        Map<String, Object> params = input == null ? Map.of() : input;
        String externalSessionId =
                Optional.ofNullable(request.getHeader(SESSION_ID_HEADER))
                        .map(String::trim)
                        .orElse(DEFAULT_SESSION);
        String sessionId =
                SessionIdFactory.build(CLIENT_AGENT_CODE, user.userId(), externalSessionId);
        ToolCallContext context =
                ToolCallContext.builder()
                        .sessionId(sessionId)
                        .userId(user.userId())
                        .datasourceIds(
                                dataPermissions.accessibleDatasourceIds(user).stream()
                                        .sorted()
                                        .toList())
                        .build();

        long startedAt = System.nanoTime();
        ToolResultBo result = invoker.invoke(name, params, context);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        String traceId = currentTraceId();
        log.info(
                "Agent tool call: tool={}, userId={}, sessionId={}, durationMs={}, traceId={}",
                name,
                user.userId(),
                sessionId,
                durationMs,
                traceId);
        return new ToolResultVo(result.text(), result.metadata(), traceId);
    }

    private String currentTraceId() {
        var span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}

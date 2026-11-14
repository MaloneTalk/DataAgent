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
package io.github.malonetalk.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.bo.AgentToolDefinitionBo;
import io.github.malonetalk.model.bo.ToolResultBo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentToolInvokerTest {

    private final AgentToolInvoker invoker =
            new AgentToolInvoker(
                    List.of(new ExposedTool(), new AgentOnlyTool()), new ObjectMapper());

    AgentToolInvokerTest() {
        invoker.init();
    }

    @Test
    void listsAllRestToolsFromOneBean() {
        List<AgentToolDefinitionBo> tools = invoker.list();

        assertEquals(2, tools.size());
        AgentToolDefinitionBo echo =
                tools.stream().filter(tool -> "echo".equals(tool.name())).findFirst().orElseThrow();
        assertEquals("Echo a message.", echo.description());
        Map<?, ?> properties = (Map<?, ?>) echo.parameters().get("properties");
        assertTrue(properties.containsKey("message"));
        assertTrue(tools.stream().anyMatch(tool -> "ping".equals(tool.name())));
    }

    @Test
    void invokesToolAndInjectsContext() {
        ToolResultBo result =
                invoker.invoke(
                        "echo",
                        Map.of("message", "hello"),
                        ToolCallContext.builder().userId(7).build());

        assertEquals("hello:7", result.text());
    }

    @Test
    void rejectsMissingRequiredParameter() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                invoker.invoke(
                                        "echo",
                                        Map.of(),
                                        ToolCallContext.builder().userId(7).build()));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void rejectsUnknownTool() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                invoker.invoke(
                                        "missing",
                                        Map.of(),
                                        ToolCallContext.builder().userId(7).build()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    private static class ExposedTool implements DataAgentTool {

        @RestTool
        @Tool(name = "echo", description = "Echo a message.")
        public ToolResultBo echo(
                @ToolParam(name = "message", description = "Message") String message,
                ToolCallContext context) {
            return new ToolResultBo(message + ":" + context.userId(), Map.of());
        }

        @RestTool
        @Tool(name = "ping", description = "Ping.")
        public ToolResultBo ping(ToolCallContext context) {
            return new ToolResultBo("pong:" + context.userId(), Map.of());
        }
    }

    private static class AgentOnlyTool implements DataAgentTool {

        @Tool(name = "internal", description = "Internal tool.")
        public ToolResultBo internal() {
            return ToolResultBo.of("internal");
        }
    }
}

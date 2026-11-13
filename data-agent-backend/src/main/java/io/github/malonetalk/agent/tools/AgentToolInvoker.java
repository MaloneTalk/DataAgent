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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.util.JsonSchemaUtils;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.bo.AgentToolDefinitionBo;
import io.github.malonetalk.model.bo.ToolResultBo;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 反射调用通过 {@link RestTool} 暴露的工具方法，并直接返回 {@link ToolResultBo}。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolInvoker {

    private final List<DataAgentTool> tools;
    private final ObjectMapper objectMapper;
    private final Map<String, ExposedTool> exposed = new LinkedHashMap<>();
    private volatile List<AgentToolDefinitionBo> definitions = List.of();

    @PostConstruct
    void init() {
        for (DataAgentTool bean : tools) {
            bean.restToolMethods().forEach(method -> register(bean, method));
        }
        // 工具 Bean 只会在启动时注册一次，列表定义无需每次请求重新生成。
        definitions = exposed.values().stream().map(this::definition).toList();
        log.info("Registered {} REST agent tools: {}", exposed.size(), exposed.keySet());
    }

    public List<AgentToolDefinitionBo> list() {
        return definitions;
    }

    public ToolResultBo invoke(String name, Map<String, Object> input, ToolCallContext context) {
        ExposedTool tool =
                Optional.ofNullable(exposed.get(name))
                        .orElseThrow(
                                () ->
                                        BusinessException.of(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Tool not found: " + name));

        Object[] arguments = new Object[tool.method().getParameterCount()];
        for (BoundParameter parameter : tool.parameters()) {
            if (parameter.type() == ToolCallContext.class) {
                arguments[parameter.index()] = context;
                continue;
            }
            Object value = input == null ? null : input.get(parameter.name());
            if (value == null) {
                if (parameter.required()) {
                    throw BusinessException.of(
                            ErrorCode.BAD_REQUEST, "Missing parameter: " + parameter.name());
                }
                arguments[parameter.index()] = null;
                continue;
            }
            JavaType javaType = objectMapper.getTypeFactory().constructType(parameter.type());
            arguments[parameter.index()] = objectMapper.convertValue(value, javaType);
        }

        try {
            Object result = tool.method().invoke(tool.bean(), arguments);
            if (result instanceof ToolResultBo toolResult) {
                return toolResult;
            }
            throw new IllegalStateException(
                    "Tool method must return ToolResultBo: " + tool.method());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private void register(DataAgentTool bean, Method method) {
        Tool tool = method.getAnnotation(Tool.class);
        if (tool == null) {
            throw new IllegalStateException("@RestTool method must also have @Tool: " + method);
        }
        method.setAccessible(true);
        String name = tool.name().isBlank() ? method.getName() : tool.name();
        ExposedTool existing =
                exposed.putIfAbsent(
                        name, new ExposedTool(bean, method, tool, bindParameters(method)));
        if (existing != null) {
            throw new IllegalStateException("Duplicate REST tool name: " + name);
        }
    }

    private List<BoundParameter> bindParameters(Method method) {
        Parameter[] parameters = method.getParameters();
        List<BoundParameter> bound = new ArrayList<>(parameters.length);
        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            if (parameter.getType() == ToolCallContext.class) {
                bound.add(new BoundParameter(null, false, parameter.getType(), index, null));
                continue;
            }
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            if (toolParam == null) {
                throw new IllegalStateException(
                        "Tool parameter must be annotated with @ToolParam: " + parameter);
            }
            bound.add(
                    new BoundParameter(
                            toolParam.name(),
                            toolParam.required(),
                            parameter.getParameterizedType(),
                            index,
                            toolParam.description()));
        }
        return bound;
    }

    private AgentToolDefinitionBo definition(ExposedTool tool) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (BoundParameter parameter : tool.parameters()) {
            if (parameter.type() == ToolCallContext.class) {
                continue;
            }
            Map<String, Object> schema = JsonSchemaUtils.generateSchemaFromType(parameter.type());
            if (parameter.description() != null && !parameter.description().isBlank()) {
                schema.put("description", parameter.description());
            }
            properties.put(parameter.name(), schema);
            if (parameter.required()) {
                required.add(parameter.name());
            }
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        if (!required.isEmpty()) {
            parameters.put("required", required);
        }

        String description =
                tool.tool().description().isBlank()
                        ? "Tool: " + tool.name()
                        : tool.tool().description();
        return new AgentToolDefinitionBo(tool.name(), description, parameters);
    }

    private record BoundParameter(
            String name, boolean required, Type type, int index, String description) {}

    private record ExposedTool(
            DataAgentTool bean, Method method, Tool tool, List<BoundParameter> parameters) {
        String name() {
            return tool.name().isBlank() ? method.getName() : tool.name();
        }
    }
}

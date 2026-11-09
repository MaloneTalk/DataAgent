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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.springframework.util.ClassUtils;

public interface DataAgentTool {

    /**
     * 返回暴露为 REST 的工具方法；空列表表示仅 Agent 内部调用。
     *
     * <p>默认从当前工具类中查找带 {@link RestTool} 的方法，具体工具类不需要覆写。
     * 与 AgentScope 一致，一个工具类可以声明多个 {@code @Tool} / {@code @RestTool} 方法。
     */
    default List<Method> restToolMethods() {
        Class<?> type = ClassUtils.getUserClass(getClass());
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(RestTool.class))
                .toList();
    }
}

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

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.DefaultToolResultConverter;
import io.agentscope.core.tool.ToolResultConverter;
import io.github.malonetalk.model.bo.ToolResultBo;
import java.lang.reflect.Type;

/** Converts {@link ToolResultBo} into the AgentScope result block; other values use the default. */
public class ToolResultBoConverter implements ToolResultConverter {

    private static final DefaultToolResultConverter DEFAULT = new DefaultToolResultConverter();

    @Override
    public ToolResultBlock convert(Object result, Type returnType) {
        if (result instanceof ToolResultBo bo) {
            return ToolResultBlock.of(TextBlock.builder().text(bo.text()).build(), bo.metadata());
        }
        return DEFAULT.convert(result, returnType);
    }
}

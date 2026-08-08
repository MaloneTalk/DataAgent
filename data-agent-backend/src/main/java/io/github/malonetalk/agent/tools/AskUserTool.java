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

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.ToolSuspendException;
import io.github.malonetalk.agent.ToolCallContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AskUserTool implements MarkAgentTool {

    @Tool(
            name = ToolCallConstants.ASK_USER,
            description =
                    "Ask the user a question when an operation is unclear or requires"
                            + " confirmation. Execution resumes after the user responds.")
    public String askUser(
            @ToolParam(name = "question", description = "The question to ask the user.")
                    String question,
            ToolCallContext ctx) {
        log.info("Agent asks user: {}", question);
        if (!ctx.allowUserPrompt()) {
            return "Cannot ask the user during this run. Explain what information is missing and"
                    + " stop.";
        }
        throw new ToolSuspendException(question);
    }
}

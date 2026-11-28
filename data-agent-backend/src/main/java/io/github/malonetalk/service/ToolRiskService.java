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
package io.github.malonetalk.service;

import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.exception.BusinessException;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Shared risk-check entry point for AgentScope and REST/CLI tool calls. */
@Service
public class ToolRiskService {

    /**
     * Currently allows every call. Add rules here, throwing {@link BusinessException} with the
     * rejection reason and hint in its message. Identity and scope must come from {@code context}.
     */
    public void requireAllowed(
            ToolCallContext context, String toolName, Map<String, Object> input) {
        // Concrete risk rules are intentionally deferred.
    }
}

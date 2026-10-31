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

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** 会话 ID 编解码：格式为 {@code <AGENT_CODE>_<userId>_<rest>}。 */
public final class SessionIdFactory {

    public static final String AGENT_SCOPE = "AGENTSCOPE";

    private static final Pattern AGENT_CODE_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9]{0,31}$");

    private SessionIdFactory() {}

    /** 校验并归一化 Agent 类型码；不允许包含下划线，统一转大写。 */
    public static String normalizeAgentCode(String agentCode) {
        if (agentCode == null) {
            throw new IllegalArgumentException("agentCode cannot be null");
        }
        String normalized = agentCode.trim().toUpperCase(Locale.ROOT);
        if (!AGENT_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid agentCode: " + agentCode);
        }
        return normalized;
    }

    /** 生成会话 ID 的基础形式；外部会话 ID 为空时使用 UUID。 */
    public static String build(String agentCode, int userId, String externalSessionId) {
        String rest =
                StringUtils.hasText(externalSessionId)
                        ? externalSessionId
                        : UUID.randomUUID().toString();
        return normalizeAgentCode(agentCode) + "_" + userId + "_" + rest;
    }

    /** 判断会话 ID 是否属于指定 Agent。 */
    public static boolean isAgent(String sessionId, String agentCode) {
        return sessionId != null && sessionId.startsWith(normalizeAgentCode(agentCode) + "_");
    }
}

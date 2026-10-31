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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionIdFactoryTest {

    @Test
    void normalizesAgentCodeToUpperCase() {
        assertEquals("AGENTSCOPE", SessionIdFactory.normalizeAgentCode(" agentscope "));
    }

    @Test
    void rejectsAgentCodeWithUnderscore() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SessionIdFactory.normalizeAgentCode("MY_AGENT"));
    }

    @Test
    void rejectsAgentCodeStartingWithDigit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SessionIdFactory.normalizeAgentCode("1AGENT"));
    }

    @Test
    void buildsPrefixedSessionIdWithExternalId() {
        assertEquals("OTHER_12_abc_123", SessionIdFactory.build("other", 12, "abc_123"));
    }

    @Test
    void buildsPrefixedSessionIdWithGeneratedUuid() {
        String sessionId = SessionIdFactory.build("agentscope", 7, null);

        assertTrue(sessionId.startsWith("AGENTSCOPE_7_"));
        assertTrue(sessionId.length() > "AGENTSCOPE_7_".length());
    }

    @Test
    void matchesExactAgentPrefixOnly() {
        assertTrue(SessionIdFactory.isAgent("AGENTSCOPE_1_x", "agentscope"));
        assertFalse(SessionIdFactory.isAgent("OTHER_1_x", "AGENTSCOPE"));
        assertFalse(SessionIdFactory.isAgent("AGENTSCOPEX_1_x", "AGENTSCOPE"));
    }
}

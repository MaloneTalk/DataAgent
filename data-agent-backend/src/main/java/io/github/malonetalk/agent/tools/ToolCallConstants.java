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

public final class ToolCallConstants {

    private ToolCallConstants() {
        throw new IllegalCallerException("No ToolNameConstants for you!");
    }

    public static final String ASK_USER = "ask_user";

    public static final String GENERATE_REPORT = "generate_report";

    public static final String SUCCESS = "SUCCESS";

    public static final String SEPARATOR = ": ";

    public static final String FAIL = "FAIL";

    public static final String SUCCESS_PREFIX = SUCCESS + SEPARATOR;

    public static final String FAIL_PREFIX = FAIL + SEPARATOR;
}

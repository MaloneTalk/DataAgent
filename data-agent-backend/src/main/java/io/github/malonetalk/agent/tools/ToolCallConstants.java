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

    /**
     * 追加在 get_tables / get_table_schema 的返回结果末尾。模型写 SQL 前基本必看表结构,
     * 在那个时机提醒「先确认指标口径」比在 system prompt 里说更准——此时它正准备动笔。
     * 数据源没定义任何指标时,get_metric_caliber 会回「未定义」,模型自然跳过。
     */
    public static final String METRIC_CALIBER_REMINDER =
            """

            If the question involves a business metric, call get_metric_caliber to confirm its \
            caliber before writing SQL.\
            """;

    public static final String SEPARATOR = ": ";

    public static final String SUCCESS_PREFIX = SUCCESS + SEPARATOR;
}

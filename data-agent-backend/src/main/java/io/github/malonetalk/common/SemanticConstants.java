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
package io.github.malonetalk.common;

/**
 * 语义层常量定义
 */
public final class SemanticConstants {

    private SemanticConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 关系键分隔符，用于构建唯一的关系标识
     */
    public static final String RELATION_KEY_SEPARATOR = "|";

    /**
     * 排序顺序：升序
     */
    public static final String SORT_ORDER_ASC = "asc";

    /**
     * 排序顺序：降序
     */
    public static final String SORT_ORDER_DESC = "desc";

    /**
     * 每张表在 Agent prompt 中返回的最大关系数量
     */
    public static final int MAX_RELATIONS_PER_TABLE = 20;
}

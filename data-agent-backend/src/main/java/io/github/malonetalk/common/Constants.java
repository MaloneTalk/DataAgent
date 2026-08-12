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

public final class Constants {

    public static final String SORT_ORDER_ASC = "asc";
    public static final String SORT_ORDER_DESC = "desc";

    /** 管理员角色 id：AuthInterceptor 靠 role_id==1 判 @AdminOnly，删掉即永久锁死管理员。 */
    public static final int ADMIN_ROLE_ID = 1;

    public static final String PROPERTIES_PREFIX = "io.github.malonetalk";

    private Constants() {
        throw new IllegalCallerException("No Constants Instance for You!");
    }
}

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
 * 当前登录用户的轻量投影，由拦截器在同步请求线程放入 ThreadLocal。
 *
 * <p>仅承载鉴权必要字段（不含 password_hash），供管理/会话等同步 API 取用。Agent 异步链路
 * （Reactor 弹性线程）拿不到此 ThreadLocal——权限轮次会改为通过 ToolCallContext 显式传 userId。
 *
 * <p>{@code roleId} 用于 @AdminOnly 权限判定：1=管理员，其他值=普通用户。
 */
public record UserContext(Integer userId, String username, String displayName, Integer roleId) {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static UserContext require() {
        UserContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("No user context bound to current thread.");
        }
        return context;
    }

    public static Integer requireScopedUserId() {
        return require().scopedUserId();
    }

    public Integer scopedUserId() {
        if (isAdmin()) {
            return null;
        }
        return userId;
    }

    public boolean isAdmin() {
        return roleId != null && roleId == Constants.ADMIN_ROLE_ID;
    }
}

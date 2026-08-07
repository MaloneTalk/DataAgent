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
package io.github.malonetalk.interceptor;

import io.github.malonetalk.annotation.AdminOnly;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.UserContext;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.SysUserMapper;
import io.github.malonetalk.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：拦 /api/**，放行 /api/auth/login 与 /error。
 *
 * <p>解析 Authorization: Bearer → JwtUtil 取 userId → 每次请求查库（selectAuthProjection 只返回启用用户）
 * → 放入 UserContext。token 缺失/过期/非法 或 用户被禁用 一律抛 {@link ErrorCode#UNAUTHORIZED}，
 * 由 GlobalExceptionHandler 统一输出 401。每次查库保证禁用即时生效。
 *
 * <p>方法/类上有 {@link AdminOnly} 且当前用户 role_id != 1 时返回
 * {@link ErrorCode#FORBIDDEN} (403)。表/列拦截、会话隔离 = 后续轮次。
 */
@Component
@AllArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final int ADMIN_ROLE_ID = 1;

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        Integer userId = jwtUtil.parseUserId(extractBearer(request));
        if (userId == null) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "Missing or invalid token.");
        }
        UserContext context = sysUserMapper.selectAuthProjection(userId);
        if (context == null) {
            // 用户不存在或 status=0（禁用），均视为未授权。
            throw BusinessException.of(
                    ErrorCode.UNAUTHORIZED, "Account is disabled or does not exist.");
        }
        UserContext.set(context);

        if (handler instanceof HandlerMethod handlerMethod && isAdminRequired(handlerMethod)) {
            if (context.roleId() == null || context.roleId() != ADMIN_ROLE_ID) {
                throw BusinessException.of(ErrorCode.FORBIDDEN, "需要管理员权限");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        UserContext.clear();
    }

    /** 方法或所在类上有 @AdminOnly 注解时要求管理员权限；方法级注解覆盖类级。 */
    private boolean isAdminRequired(HandlerMethod handlerMethod) {
        AdminOnly methodAnnotation = handlerMethod.getMethodAnnotation(AdminOnly.class);
        if (methodAnnotation != null) {
            return true;
        }
        return handlerMethod.getBeanType().isAnnotationPresent(AdminOnly.class);
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        String trimmed = header.trim();
        String prefix = "Bearer ";
        if (trimmed.length() > prefix.length()
                && trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return trimmed.substring(prefix.length()).trim();
        }
        return null;
    }
}

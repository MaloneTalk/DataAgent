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

import io.github.malonetalk.annotation.RequirePermission;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.UserContext;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.service.SysUserService;
import io.github.malonetalk.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：拦 /api/**，放行 /api/auth/login 与 /error。
 *
 * <p>方法/类上有 {@link RequirePermission} 且当前用户没有相应权限时返回
 * {@link ErrorCode#FORBIDDEN} (403)。表/列拦截、会话隔离 = 后续轮次。规定超级管理员不需要校验权限。
 */
@Component
@AllArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SysUserService sysUserService;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        Integer userId = jwtUtil.parseUserId(extractBearer(request));
        if (userId == null) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "Missing or invalid token.");
        }
        UserContext context = sysUserService.selectAuthProjection(userId);
        if (context == null) {
            // 用户不存在或 status=0（禁用），均视为未授权。
            throw BusinessException.of(
                    ErrorCode.UNAUTHORIZED, "Account is disabled or does not exist.");
        }
        UserContext.set(context);

        // 如果没有权限校验注解，直接返回
        if (!(handler instanceof HandlerMethod handlerMethod
                && hashRequiredAnnotation(handlerMethod))) {
            return true;
        }

        if (Boolean.TRUE.equals(context.superAdmin())) {
            return true;
        }
        checkPermission(context);

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

    private boolean hashRequiredAnnotation(HandlerMethod handlerMethod) {
        RequirePermission methodAnnotation =
                handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (methodAnnotation != null) {
            return true;
        }
        return handlerMethod.getBeanType().isAnnotationPresent(RequirePermission.class);
    }

    private void checkPermission(UserContext user) {
        // TODO 实现按角色、按业务类型枚举授权（Issue #150）
        throw BusinessException.of(ErrorCode.FORBIDDEN, "Missing required permission.");
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

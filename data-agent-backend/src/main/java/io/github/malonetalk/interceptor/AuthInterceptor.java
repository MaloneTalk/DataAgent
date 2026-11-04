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

import io.github.malonetalk.annotation.LoginRequired;
import io.github.malonetalk.annotation.RequirePermission;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.service.PermissionService;
import io.github.malonetalk.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 鉴权拦截器：拦 /api/**，放行 /api/auth/login 与 /error。
 *
 * <p>方法/类上有 {@link RequirePermission} 且当前用户没有相应权限时返回
 * {@link ErrorCode#FORBIDDEN} (403)。未声明访问策略的接口默认拒绝。
 */
@Component
@AllArgsConstructor
public class AuthInterceptor implements AsyncHandlerInterceptor {

    private final PermissionService permissions;
    private final JwtUtil jwtUtil;
    private final UserContextHolder userContextHolder;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContextBo context = permissions.user(jwtUtil.parseUserId(extractBearer(request)));
        userContextHolder.set(context);

        try {
            if (!(handler instanceof HandlerMethod method)) {
                throw BusinessException.of(ErrorCode.FORBIDDEN);
            }
            RequirePermission required = method.getMethodAnnotation(RequirePermission.class);
            if (required == null)
                required = method.getBeanType().getAnnotation(RequirePermission.class);
            if (required != null && required.value().length > 0) {
                if (required.anyOf()) permissions.requireAny(context, required.value());
                else permissions.require(context, required.value());
            } else if (!method.hasMethodAnnotation(LoginRequired.class)
                    && !method.getBeanType().isAnnotationPresent(LoginRequired.class)) {
                throw BusinessException.of(ErrorCode.FORBIDDEN, "Endpoint has no access policy.");
            }
            return true;
        } catch (RuntimeException ex) {
            userContextHolder.clear();
            throw ex;
        }
    }

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        userContextHolder.clear();
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        userContextHolder.clear();
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

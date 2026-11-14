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

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.bo.UserPatBo;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.service.PermissionService;
import io.github.malonetalk.service.UserPatService;
import io.github.malonetalk.utils.BearerTokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/** `/agent/**` 的 PAT 鉴权：解析 Bearer Token，加载用户并检查启用状态。 */
@Slf4j
@Component
@AllArgsConstructor
public class AgentAuthInterceptor implements AsyncHandlerInterceptor {

    public static final String AUTHENTICATED_PAT_ATTRIBUTE = "authenticatedPat";

    private final UserPatService userPatService;
    private final PermissionService permissions;
    private final UserContextHolder userContextHolder;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        var authenticated = userPatService.authenticate(BearerTokenUtils.extract(request));
        if (authenticated.isEmpty()) {
            log.warn("Agent request rejected: invalid PAT, path={}", request.getRequestURI());
            throw BusinessException.of(ErrorCode.UNAUTHORIZED);
        }
        UserPatBo pat = authenticated.get();
        UserContextBo context = permissions.user(pat.userId());
        userContextHolder.set(context);
        request.setAttribute(AUTHENTICATED_PAT_ATTRIBUTE, pat);
        return true;
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
}

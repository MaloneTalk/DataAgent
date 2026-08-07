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
package io.github.malonetalk.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 签发/校验：HS256，唯一用途是承载 userId（权限每次请求现查库，不进 token）。
 *
 * <p>密钥来源环境变量 {@code JWT_SECRET}（须 ≥ 32 字节）；未配置或过短时启动期生成随机密钥并告警——
 * 此时重启会让所有已签发 token 失效，生产环境必须配置固定密钥。
 * ponytail: 不引入刷新 token / 黑名单，登出 = 前端清 token（JWT 无状态）。
 */
@Component
@Slf4j
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMillis;

    public JwtUtil(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.expiration-hours:24}") long expirationHours) {
        this.key = resolveKey(secret);
        this.expirationMillis = expirationHours * 3600_000L;
    }

    /**
     * 签发 token，subject 为 userId 字符串。
     */
    public String generate(Integer userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 userId；token 非法/过期返回 null，由拦截器据此返回 401。
     */
    public Integer parseUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String subject =
                    Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload()
                            .getSubject();
            return Integer.valueOf(subject);
        } catch (Exception e) {
            return null;
        }
    }

    private static SecretKey resolveKey(String secret) {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES) {
            return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        log.warn(
                "jwt.secret is missing or shorter than {} bytes; generating an in-memory random"
                        + " key. All tokens will invalidate on restart. Configure JWT_SECRET for"
                        + " production.",
                MIN_SECRET_BYTES);
        // ponytail: 直接用随机字节走 hmacShaKeyFor，避开 jjwt 0.12.x secretKeyFor 重载歧义。
        byte[] randomBytes = new byte[MIN_SECRET_BYTES];
        new SecureRandom().nextBytes(randomBytes);
        return Keys.hmacShaKeyFor(randomBytes);
    }
}

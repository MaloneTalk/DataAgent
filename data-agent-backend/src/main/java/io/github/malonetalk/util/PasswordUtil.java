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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 密码哈希：JDK 标准库 PBKDF2WithHmacSHA256，零新依赖。
 *
 * <p>存储格式 {@code pbkdf2$<iter>$<base64Salt>$<base64Hash>}；迭代次数与盐长度固定，校验时从串中解析。
 * OWASP 2023 推荐 PBKDF2-SHA256 迭代 ≥ 600000，此处取 210000（兼顾百人内网规模与登录耗时），
 * ponytail: 如安全规范要求更高强度或换 Argon2，调迭代数或换算法即可，存储格式兼容。
 */
public final class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final String PREFIX = "pbkdf2";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {}

    /**
     * 生成 {@code pbkdf2$iter$salt$hash} 串。
     */
    public static String hash(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank.");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + base64(salt) + "$" + base64(hash);
    }

    /**
     * 校验明文与已存储的哈希串是否匹配；存储串格式非法或为 null 一律返回 false。
     */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            return constantTimeEquals(expected, actual);
        } catch (IllegalArgumentException e) {
            // NumberFormatException 是 IllegalArgumentException 子类，已一并覆盖。
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            SecretKey key = factory.generateSecret(spec);
            return key.getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable", e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive password hash", e);
        }
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}

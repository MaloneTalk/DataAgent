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
package io.github.malonetalk.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.UserPatMapper;
import io.github.malonetalk.model.bo.UserPatBo;
import io.github.malonetalk.model.bo.UserPatCreateResultBo;
import io.github.malonetalk.model.converter.UserPatConverter;
import io.github.malonetalk.model.dto.UserPatCreateDto;
import io.github.malonetalk.model.po.UserPatPo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPatServiceImpl implements UserPatService {

    static final int MAX_ACTIVE_PATS = 10;
    private static final int DEFAULT_EXPIRY_DAYS = 90;
    private static final int MAX_EXPIRY_DAYS = 365;
    private static final int MIN_EXPIRY_DAYS = 1;
    private static final int TOKEN_PREFIX_LENGTH = 12;
    private static final int TOKEN_BYTES = 32;
    private static final int LAST_USED_UPDATE_MINUTES = 5;
    private static final String TOKEN_PREFIX = "pat_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserPatMapper userPatMapper;
    private final UserPatConverter converter;

    @Override
    @Transactional
    public UserPatCreateResultBo create(Integer userId, UserPatCreateDto request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = resolveExpiresAt(request.expiresAt(), now);
        if (activeCount(userId, now) >= MAX_ACTIVE_PATS) {
            throw BusinessException.of(
                    ErrorCode.DATA_CONFLICT,
                    "Active personal access tokens cannot exceed " + MAX_ACTIVE_PATS + ".");
        }

        String token = generateToken();
        UserPatPo pat = new UserPatPo();
        pat.setUserId(userId);
        pat.setName(requireName(request.name()));
        pat.setTokenPrefix(tokenPrefix(token));
        pat.setTokenHash(hash(token));
        pat.setExpiresAt(expiresAt);
        if (userPatMapper.insert(pat) == 0) {
            throw BusinessException.of(ErrorCode.OPERATION_FAILED, "Failed to create the token.");
        }
        log.info(
                "PAT created: userId={}, patId={}, name={}, expiresAt={}",
                userId,
                pat.getId(),
                pat.getName(),
                expiresAt);
        return new UserPatCreateResultBo(converter.toBo(pat), token);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserPatBo> list(Integer userId) {
        return userPatMapper
                .selectList(
                        Wrappers.<UserPatPo>lambdaQuery()
                                .select(
                                        UserPatPo::getId,
                                        UserPatPo::getName,
                                        UserPatPo::getTokenPrefix,
                                        UserPatPo::getExpiresAt,
                                        UserPatPo::getLastUsedAt,
                                        UserPatPo::getRevokedAt,
                                        UserPatPo::getCreateTime)
                                .eq(UserPatPo::getUserId, userId)
                                .orderByDesc(UserPatPo::getCreateTime))
                .stream()
                .map(converter::toBo)
                .toList();
    }

    @Override
    @Transactional
    public void revoke(Integer userId, Long id) {
        UserPatPo existing =
                userPatMapper.selectOne(
                        Wrappers.<UserPatPo>lambdaQuery()
                                .select(
                                        UserPatPo::getId,
                                        UserPatPo::getUserId,
                                        UserPatPo::getRevokedAt)
                                .eq(UserPatPo::getId, id));
        if (existing == null || !Objects.equals(existing.getUserId(), userId)) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "Token not found.");
        }
        if (existing.getRevokedAt() != null) {
            return;
        }

        UserPatPo update = new UserPatPo();
        update.setId(id);
        update.setRevokedAt(LocalDateTime.now());
        if (userPatMapper.updateById(update) == 0) {
            throw BusinessException.of(ErrorCode.OPERATION_FAILED, "Failed to revoke the token.");
        }
        log.info("PAT revoked: userId={}, patId={}", userId, id);
    }

    @Override
    @Transactional
    public Optional<UserPatBo> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String token = rawToken.trim();
        if (!token.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }

        String presentedHash = hash(token);
        UserPatPo pat = userPatMapper.selectByTokenHash(presentedHash);
        if (pat == null || !constantTimeEquals(presentedHash, pat.getTokenHash())) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        if (pat.getRevokedAt() != null) {
            return Optional.empty();
        }
        if (pat.getExpiresAt() == null || !pat.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }

        updateLastUsedAt(pat, now);
        return Optional.of(converter.toBo(pat));
    }

    private void updateLastUsedAt(UserPatPo pat, LocalDateTime now) {
        if (pat.getLastUsedAt() != null
                && pat.getLastUsedAt().isAfter(now.minusMinutes(LAST_USED_UPDATE_MINUTES))) {
            return;
        }
        UserPatPo update = new UserPatPo();
        update.setId(pat.getId());
        update.setLastUsedAt(now);
        userPatMapper.updateById(update);
    }

    /** 常量时间比较，避免普通字符串比较按首个差异字节提前返回导致时序侧信道。 */
    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private Long activeCount(Integer userId, LocalDateTime now) {
        return userPatMapper.selectCount(
                Wrappers.<UserPatPo>lambdaQuery()
                        .eq(UserPatPo::getUserId, userId)
                        .isNull(UserPatPo::getRevokedAt)
                        .gt(UserPatPo::getExpiresAt, now));
    }

    private static String requireName(String name) {
        return Optional.ofNullable(name)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElseThrow(
                        () -> BusinessException.of(ErrorCode.BAD_REQUEST, "Name is required."));
    }

    private static LocalDateTime resolveExpiresAt(LocalDateTime requested, LocalDateTime now) {
        LocalDateTime expiresAt =
                Optional.ofNullable(requested).orElse(now.plusDays(DEFAULT_EXPIRY_DAYS));
        if (expiresAt.isBefore(now.plusDays(MIN_EXPIRY_DAYS))) {
            throw BusinessException.of(
                    ErrorCode.BAD_REQUEST, "Expiration must be at least 1 day later.");
        }
        if (expiresAt.isAfter(now.plusDays(MAX_EXPIRY_DAYS))) {
            throw BusinessException.of(
                    ErrorCode.BAD_REQUEST,
                    "Expiration cannot exceed " + MAX_EXPIRY_DAYS + " days.");
        }
        return expiresAt;
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String tokenPrefix(String token) {
        return token.substring(0, Math.min(TOKEN_PREFIX_LENGTH, token.length()));
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}

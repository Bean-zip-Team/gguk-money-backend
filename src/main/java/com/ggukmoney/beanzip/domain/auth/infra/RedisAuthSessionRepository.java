package com.ggukmoney.beanzip.domain.auth.infra;

import com.ggukmoney.beanzip.domain.auth.model.AuthSession;
import com.ggukmoney.beanzip.domain.auth.model.RefreshRotationResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RedisAuthSessionRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisAuthSessionRepository.class);
    private static final Duration ACCESS_REVOKE_TTL = Duration.ofMinutes(20);
    private static final long REFRESH_CONFLICT_GRACE_MILLIS = 2_000L;

    private static final String ROTATE_REFRESH_LUA = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 0
            end
            local currentJti = redis.call('HGET', KEYS[1], 'currentRefreshJtiHash')
            local currentToken = redis.call('HGET', KEYS[1], 'refreshTokenHash')
            local previousJti = redis.call('HGET', KEYS[1], 'previousRefreshJtiHash')
            local rotatedAtMillis = redis.call('HGET', KEYS[1], 'rotatedAtEpochMillis')
            if currentJti ~= ARGV[1] or currentToken ~= ARGV[2] then
              if previousJti == ARGV[1] then
                if rotatedAtMillis and rotatedAtMillis ~= '' then
                  local elapsedMillis = tonumber(ARGV[9]) - tonumber(rotatedAtMillis)
                  if elapsedMillis >= 0 and elapsedMillis <= tonumber(ARGV[10]) then
                    return 2
                  end
                end
                return 3
              end
              return 2
            end
            redis.call('HSET', KEYS[1],
              'previousRefreshJtiHash', currentJti,
              'currentRefreshJtiHash', ARGV[3],
              'refreshTokenHash', ARGV[4],
              'rotatedAt', ARGV[5],
              'rotatedAtEpochMillis', ARGV[9],
              'expiresAt', ARGV[6],
              'status', 'ACTIVE')
            redis.call('PEXPIREAT', KEYS[1], ARGV[7])
            redis.call('ZADD', KEYS[2], ARGV[7], ARGV[8])
            return 1
            """;

    private final StringRedisTemplate redisTemplate;

    public void save(AuthSession session) {
        String refreshKey = refreshKey(session.sessionId());
        redisTemplate.opsForHash().putAll(refreshKey, Map.of(
                "userPublicId", session.userPublicId(),
                "devicePublicId", session.devicePublicId(),
                "currentRefreshJtiHash", session.currentRefreshJtiHash(),
                "refreshTokenHash", session.refreshTokenHash(),
                "tokenFamilyIdHash", session.tokenFamilyIdHash(),
                "previousRefreshJtiHash", valueOrEmpty(session.previousRefreshJtiHash()),
                "rotatedAt", valueOrEmpty(session.rotatedAt()),
                "issuedAt", session.issuedAt().toString(),
                "expiresAt", session.expiresAt().toString(),
                "status", session.status()
        ));
        redisTemplate.expire(refreshKey, Duration.between(Instant.now(), session.expiresAt()));
        redisTemplate.opsForZSet().add(userSessionsKey(session.userPublicId()), session.sessionId().toString(), session.expiresAt().toEpochMilli());
    }

    public Optional<AuthSession> findBySessionId(UUID sessionId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(refreshKey(sessionId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AuthSession(
                sessionId,
                required(entries, "userPublicId"),
                required(entries, "devicePublicId"),
                required(entries, "currentRefreshJtiHash"),
                required(entries, "refreshTokenHash"),
                required(entries, "tokenFamilyIdHash"),
                blankToNull(required(entries, "previousRefreshJtiHash")),
                parseNullableInstant(required(entries, "rotatedAt")),
                Instant.parse(required(entries, "issuedAt")),
                Instant.parse(required(entries, "expiresAt")),
                required(entries, "status")
        ));
    }

    public RefreshRotationResult rotateRefreshToken(
            AuthSession session,
            String expectedCurrentRefreshJtiHash,
            String expectedRefreshTokenHash,
            String newCurrentRefreshJtiHash,
            String newRefreshTokenHash,
            Instant rotatedAt,
            Instant expiresAt
    ) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ROTATE_REFRESH_LUA, Long.class);
        Long result = redisTemplate.execute(
                script,
                List.of(refreshKey(session.sessionId()), userSessionsKey(session.userPublicId())),
                expectedCurrentRefreshJtiHash,
                expectedRefreshTokenHash,
                newCurrentRefreshJtiHash,
                newRefreshTokenHash,
                rotatedAt.toString(),
                expiresAt.toString(),
                String.valueOf(expiresAt.toEpochMilli()),
                session.sessionId().toString(),
                String.valueOf(rotatedAt.toEpochMilli()),
                String.valueOf(REFRESH_CONFLICT_GRACE_MILLIS)
        );

        if (result == null || result == 0L) {
            return RefreshRotationResult.NOT_FOUND;
        }
        if (result == 1L) {
            return RefreshRotationResult.ROTATED;
        }
        if (result == 3L) {
            return RefreshRotationResult.REUSED;
        }
        return RefreshRotationResult.CONFLICT;
    }

    public void deleteSession(UUID sessionId) {
        findBySessionId(sessionId).ifPresent(session -> {
            redisTemplate.delete(refreshKey(sessionId));
            redisTemplate.opsForZSet().remove(userSessionsKey(session.userPublicId()), sessionId.toString());
        });
    }

    public void addAccessDeny(String jti, Instant expiresAt) {
        String key = "auth:deny:access:" + jti;
        redisTemplate.opsForValue().set(key, "1", Duration.between(Instant.now(), expiresAt));
    }

    public boolean isAccessDenied(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("auth:deny:access:" + jti));
    }

    public void revokeUser(String userPublicId, Instant revokedAt) {
        redisTemplate.opsForValue().set(revokeUserKey(userPublicId), String.valueOf(revokedAt.toEpochMilli()));
    }

    public long revokeAllUserSessions(
            String userPublicId,
            String accessJti,
            Instant accessExpiresAt,
            Instant revokedAt,
            String reason
    ) {
        try {
            String userSessionsKey = userSessionsKey(userPublicId);
            long revokedAtMillis = revokedAt.toEpochMilli();
            redisTemplate.opsForZSet().removeRangeByScore(userSessionsKey, Double.NEGATIVE_INFINITY, revokedAtMillis);
            Set<String> sessionIds = redisTemplate.opsForZSet().range(userSessionsKey, 0, -1);
            long revokedCount = sessionIds == null ? 0 : sessionIds.size();

            redisTemplate.opsForValue().set(
                    revokeUserKey(userPublicId),
                    revokeMarker(revokedAtMillis, reason),
                    ACCESS_REVOKE_TTL
            );

            if (sessionIds != null && !sessionIds.isEmpty()) {
                redisTemplate.delete(refreshKeys(sessionIds));
            }
            redisTemplate.delete(userSessionsKey);
            if (StringUtils.hasText(accessJti) && accessExpiresAt != null) {
                addAccessDeny(accessJti, accessExpiresAt);
            }
            return revokedCount;
        } catch (RuntimeException exception) {
            log.error("Failed to revoke all Redis auth sessions for userPublicId={}", userPublicId, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_REDIS_UNAVAILABLE", exception);
        }
    }

    public Optional<Long> findUserRevokedAtMillis(String userPublicId) {
        String value = redisTemplate.opsForValue().get(revokeUserKey(userPublicId));
        return value == null ? Optional.empty() : Optional.of(parseRevokedAtMillis(value));
    }

    public static String refreshKey(UUID sessionId) {
        return "auth:refresh:" + sessionId;
    }

    public static String userSessionsKey(String userPublicId) {
        return "auth:user-sessions:" + userPublicId;
    }

    private String revokeUserKey(String userPublicId) {
        return "auth:revoke:user:" + userPublicId;
    }

    private Collection<String> refreshKeys(Set<String> sessionIds) {
        List<String> keys = new ArrayList<>();
        for (String sessionId : sessionIds) {
            keys.add("auth:refresh:" + sessionId);
        }
        return keys;
    }

    private String revokeMarker(long revokedAtMillis, String reason) {
        return "{\"revokedAtMillis\":" + revokedAtMillis + ",\"reason\":\"" + reason + "\"}";
    }

    private long parseRevokedAtMillis(String value) {
        if (!value.startsWith("{")) {
            return Long.parseLong(value);
        }

        String fieldName = "\"revokedAtMillis\":";
        int fieldStart = value.indexOf(fieldName);
        if (fieldStart < 0) {
            throw new IllegalArgumentException("revokedAtMillis is missing");
        }
        int numberStart = fieldStart + fieldName.length();
        int numberEnd = value.indexOf(',', numberStart);
        if (numberEnd < 0) {
            numberEnd = value.indexOf('}', numberStart);
        }
        if (numberEnd < 0) {
            throw new IllegalArgumentException("revokedAtMillis is malformed");
        }
        return Long.parseLong(value.substring(numberStart, numberEnd));
    }

    private String required(Map<Object, Object> entries, String key) {
        Object value = entries.get(key);
        return value == null ? "" : value.toString();
    }

    private String valueOrEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Instant parseNullableInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}

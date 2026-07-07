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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final String REVOKE_ALL_USER_SESSIONS_LUA = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local sessionIds = redis.call('ZRANGE', KEYS[1], 0, -1)
            local revokedCount = 0
            for _, sessionId in ipairs(sessionIds) do
              local refreshKey = 'auth:refresh:' .. sessionId
              if redis.call('EXISTS', refreshKey) == 1 then
                redis.call('DEL', refreshKey)
                revokedCount = revokedCount + 1
              end
            end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            if ARGV[4] and ARGV[4] ~= '' and ARGV[5] and ARGV[5] ~= '' then
              local denyTtl = tonumber(ARGV[5]) - tonumber(ARGV[1])
              if denyTtl > 0 then
                redis.call('SET', 'auth:deny:access:' .. ARGV[4], '1', 'PX', denyTtl)
              end
            end
            return revokedCount
            """;

    private final StringRedisTemplate redisTemplate;

    public void save(AuthSession session) {
        String refreshKey = refreshKey(session.sessionId());
        redisTemplate.opsForHash().putAll(refreshKey, Map.of(
                "userId", session.userId().toString(),
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
        redisTemplate.opsForZSet().add(userSessionsKey(session.userId()), session.sessionId().toString(), session.expiresAt().toEpochMilli());
    }

    public Optional<AuthSession> findBySessionId(UUID sessionId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(refreshKey(sessionId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AuthSession(
                sessionId,
                UUID.fromString(required(entries, "userId")),
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
                List.of(refreshKey(session.sessionId()), userSessionsKey(session.userId())),
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
            redisTemplate.opsForZSet().remove(userSessionsKey(session.userId()), sessionId.toString());
        });
    }

    public void addAccessDeny(String jti, Instant expiresAt) {
        String key = "auth:deny:access:" + jti;
        redisTemplate.opsForValue().set(key, "1", Duration.between(Instant.now(), expiresAt));
    }

    public boolean isAccessDenied(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("auth:deny:access:" + jti));
    }

    public void revokeUser(UUID userId, Instant revokedAt) {
        redisTemplate.opsForValue().set(revokeUserKey(userId), String.valueOf(revokedAt.toEpochMilli()));
    }

    public long revokeAllUserSessions(
            UUID userId,
            String accessJti,
            Instant accessExpiresAt,
            Instant revokedAt,
            String reason
    ) {
        try {
            String userSessionsKey = userSessionsKey(userId);
            long revokedAtMillis = revokedAt.toEpochMilli();
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(REVOKE_ALL_USER_SESSIONS_LUA, Long.class);
            Long revokedCount = redisTemplate.execute(
                    script,
                    List.of(userSessionsKey, revokeUserKey(userId)),
                    String.valueOf(revokedAtMillis),
                    revokeMarker(revokedAtMillis, reason),
                    String.valueOf(ACCESS_REVOKE_TTL.toMillis()),
                    StringUtils.hasText(accessJti) ? accessJti : "",
                    accessExpiresAt == null ? "" : String.valueOf(accessExpiresAt.toEpochMilli())
            );
            if (revokedCount == null) {
                throw new IllegalStateException("Redis logout-all script returned null");
            }
            return revokedCount;
        } catch (RuntimeException exception) {
            log.error("Failed to revoke all Redis auth sessions for userId={}", userId, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_REDIS_UNAVAILABLE", exception);
        }
    }

    public Optional<Long> findUserRevokedAtMillis(UUID userId) {
        String value = redisTemplate.opsForValue().get(revokeUserKey(userId));
        return value == null ? Optional.empty() : Optional.of(parseRevokedAtMillis(value));
    }

    public static String refreshKey(UUID sessionId) {
        return "auth:refresh:" + sessionId;
    }

    public static String userSessionsKey(UUID userId) {
        return "auth:user-sessions:" + userId;
    }

    private String revokeUserKey(UUID userId) {
        return "auth:revoke:user:" + userId;
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

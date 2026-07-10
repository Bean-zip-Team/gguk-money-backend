package com.ggukmoney.beanzip.domain.tap.infra;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed per-user token bucket for the tap-batch endpoint, plus a fixed-window
 * per-minute counter used by {@link com.ggukmoney.beanzip.domain.tap.service.TapValidityCalculator}
 * to cap the accepted tap count. Mirrors the Lua-script pattern used by
 * {@code RedisAuthSessionRepository} so every mutation is atomic.
 */
@Repository
@RequiredArgsConstructor
public class TapRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TapRateLimiter.class);
    private static final Duration MINUTE_WINDOW = Duration.ofSeconds(60);

    private static final String TOKEN_BUCKET_LUA = """
            local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
            local updatedAt = tonumber(redis.call('HGET', KEYS[1], 'updatedAtMillis'))
            local capacity = tonumber(ARGV[1])
            local refillPerSecond = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            if tokens == nil then
              tokens = capacity
              updatedAt = now
            end

            local elapsedSeconds = (now - updatedAt) / 1000.0
            if elapsedSeconds > 0 then
              tokens = math.min(capacity, tokens + elapsedSeconds * refillPerSecond)
            end

            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end

            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'updatedAtMillis', tostring(now))
            local ttlSeconds = math.ceil(capacity / refillPerSecond) * 2
            redis.call('EXPIRE', KEYS[1], ttlSeconds)

            return allowed
            """;

    private static final String INCR_WITH_WINDOW_LUA = """
            local newValue = redis.call('INCRBY', KEYS[1], ARGV[1])
            if tonumber(newValue) == tonumber(ARGV[1]) then
              redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return newValue
            """;

    private final StringRedisTemplate redisTemplate;

    public boolean tryConsume(UUID userId, int capacity, double refillPerSecond) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);
            Long allowed = redisTemplate.execute(
                    script,
                    List.of(bucketKey(userId)),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSecond),
                    String.valueOf(Instant.now().toEpochMilli())
            );
            return allowed != null && allowed == 1L;
        } catch (RuntimeException exception) {
            log.error("Failed to evaluate tap rate limit for userId={}", userId, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TAP_REDIS_UNAVAILABLE", exception);
        }
    }

    public int getMinuteCount(UUID userId) {
        String value = redisTemplate.opsForValue().get(minuteKey(userId));
        return value == null ? 0 : Integer.parseInt(value);
    }

    public void addMinuteCount(UUID userId, int delta) {
        if (delta <= 0) {
            return;
        }
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_WITH_WINDOW_LUA, Long.class);
            redisTemplate.execute(
                    script,
                    List.of(minuteKey(userId)),
                    String.valueOf(delta),
                    String.valueOf(MINUTE_WINDOW.toSeconds())
            );
        } catch (RuntimeException exception) {
            log.error("Failed to increment tap minute counter for userId={}", userId, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TAP_REDIS_UNAVAILABLE", exception);
        }
    }

    private String bucketKey(UUID userId) {
        return "tap:bucket:" + userId;
    }

    private String minuteKey(UUID userId) {
        return "tap:minute:" + userId;
    }
}

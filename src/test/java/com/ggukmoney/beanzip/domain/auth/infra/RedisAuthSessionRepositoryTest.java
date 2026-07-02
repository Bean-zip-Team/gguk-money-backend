package com.ggukmoney.beanzip.domain.auth.infra;

import com.ggukmoney.beanzip.domain.auth.model.AuthSession;
import com.ggukmoney.beanzip.domain.auth.model.RefreshRotationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedisAuthSessionRepositoryTest {

    @Test
    void rotatesRefreshTokenWithLuaCasAndNoLockKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        when(redisTemplate.execute(
                scriptCaptor.capture(),
                keysCaptor.capture(),
                eq("refresh-jti-old-hash"),
                eq("refresh-token-old-hash"),
                eq("refresh-jti-new-hash"),
                eq("refresh-token-new-hash"),
                eq("2026-07-02T00:01:00Z"),
                eq("2026-08-01T00:01:00Z"),
                eq("1785542460000"),
                eq("00000000-0000-0000-0000-000000000001"),
                eq("1782950460000"),
                eq("2000")
        )).thenReturn(1L);

        RedisAuthSessionRepository repository = new RedisAuthSessionRepository(redisTemplate);
        AuthSession session = new AuthSession(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "usr_public_1",
                "device_public_1",
                "refresh-jti-old-hash",
                "refresh-token-old-hash",
                "family-hash",
                null,
                null,
                Instant.parse("2026-07-02T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "ACTIVE"
        );

        RefreshRotationResult result = repository.rotateRefreshToken(
                session,
                "refresh-jti-old-hash",
                "refresh-token-old-hash",
                "refresh-jti-new-hash",
                "refresh-token-new-hash",
                Instant.parse("2026-07-02T00:01:00Z"),
                Instant.parse("2026-08-01T00:01:00Z")
        );

        assertThat(result).isEqualTo(RefreshRotationResult.ROTATED);
        assertThat(keysCaptor.getValue()).containsExactly(
                "auth:refresh:00000000-0000-0000-0000-000000000001",
                "auth:user-sessions:usr_public_1"
        );
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("redis.call('HGET', KEYS[1], 'currentRefreshJtiHash')")
                .contains("redis.call('HSET', KEYS[1]")
                .contains("redis.call('ZADD', KEYS[2]");
        assertThat(scriptCaptor.getValue().getScriptAsString()).doesNotContain("lock:auth:" + "refresh");
    }

    @Test
    void revokeAllUserSessionsDeletesRefreshSessionsAndStoresReasonedRevokeMarker() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(zSetOperations.range("auth:user-sessions:usr_public_1", 0, -1)).thenReturn(Set.of(
                "00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000002"
        ));

        RedisAuthSessionRepository repository = new RedisAuthSessionRepository(redisTemplate);
        long revokedCount = repository.revokeAllUserSessions(
                "usr_public_1",
                "access-jti-1",
                Instant.parse("2026-07-02T00:15:00Z"),
                Instant.parse("2026-07-02T00:00:00Z"),
                "LOGOUT_ALL"
        );

        assertThat(revokedCount).isEqualTo(2);
        verify(zSetOperations).removeRangeByScore(eq("auth:user-sessions:usr_public_1"), anyDouble(), eq(1782950400000.0));
        verify(valueOperations).set(
                eq("auth:revoke:user:usr_public_1"),
                eq("{\"revokedAtMillis\":1782950400000,\"reason\":\"LOGOUT_ALL\"}"),
                any(Duration.class)
        );
        verify(redisTemplate).delete(argThat((Collection<String> keys) -> keys.containsAll(List.of(
                "auth:refresh:00000000-0000-0000-0000-000000000001",
                "auth:refresh:00000000-0000-0000-0000-000000000002"
        ))));
        verify(redisTemplate).delete("auth:user-sessions:usr_public_1");
        verify(valueOperations).set(eq("auth:deny:access:access-jti-1"), eq("1"), any(Duration.class));
    }

    @Test
    void parsesReasonedRevokeMarkerForAccessTokenChecks() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:revoke:user:usr_public_1"))
                .thenReturn("{\"revokedAtMillis\":1782950400000,\"reason\":\"LOGOUT_ALL\"}");

        RedisAuthSessionRepository repository = new RedisAuthSessionRepository(redisTemplate);

        assertThat(repository.findUserRevokedAtMillis("usr_public_1")).contains(1782950400000L);
    }
}

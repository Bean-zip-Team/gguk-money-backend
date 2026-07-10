package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.global.service.RedisService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceRedisSessionTest {

    @Test
    void rotatesRefreshTokenWithLuaCasAndNoLockKey() {
        RedisService redisService = mock(RedisService.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.captor();
        when(redisService.executeScript(
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

        AuthService authService = new AuthService(null, redisService, null, null, null, null, null);
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        AuthService.AuthSession session = new AuthService.AuthSession(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                userId,
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

        AuthService.RefreshRotationResult result = authService.rotateRefreshToken(
                session,
                "refresh-jti-old-hash",
                "refresh-token-old-hash",
                "refresh-jti-new-hash",
                "refresh-token-new-hash",
                Instant.parse("2026-07-02T00:01:00Z"),
                Instant.parse("2026-08-01T00:01:00Z")
        );

        assertThat(result).isEqualTo(AuthService.RefreshRotationResult.ROTATED);
        assertThat(keysCaptor.getValue()).containsExactly(
                "auth:refresh:00000000-0000-0000-0000-000000000001",
                "auth:user-sessions:10000000-0000-0000-0000-000000000001"
        );
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("redis.call('HGET', KEYS[1], 'currentRefreshJtiHash')")
                .contains("redis.call('HSET', KEYS[1]")
                .contains("redis.call('ZADD', KEYS[2]");
        assertThat(scriptCaptor.getValue().getScriptAsString()).doesNotContain("lock:auth:" + "refresh");
    }

    @Test
    void revokeAllUserSessionsDeletesRefreshSessionsAndStoresReasonedRevokeMarker() {
        RedisService redisService = mock(RedisService.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.captor();
        when(redisService.executeScript(
                scriptCaptor.capture(),
                keysCaptor.capture(),
                eq("1782950400000"),
                eq("{\"revokedAtMillis\":1782950400000,\"reason\":\"LOGOUT_ALL\"}"),
                eq("1200000"),
                eq("access-jti-1"),
                eq("1782951300000")
        )).thenReturn(2L);

        AuthService authService = new AuthService(null, redisService, null, null, null, null, null);
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        long revokedCount = authService.revokeAllUserSessions(
                userId,
                "access-jti-1",
                Instant.parse("2026-07-02T00:15:00Z"),
                Instant.parse("2026-07-02T00:00:00Z"),
                "LOGOUT_ALL"
        );

        assertThat(revokedCount).isEqualTo(2);
        assertThat(keysCaptor.getValue()).containsExactly(
                "auth:user-sessions:10000000-0000-0000-0000-000000000001",
                "auth:revoke:user:10000000-0000-0000-0000-000000000001"
        );
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])")
                .contains("redis.call('DEL', refreshKey)")
                .contains("redis.call('DEL', KEYS[1])")
                .contains("redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])")
                .contains("redis.call('SET', 'auth:deny:access:' .. ARGV[4], '1', 'PX', denyTtl)");
    }

    @Test
    void parsesReasonedRevokeMarkerForAccessTokenChecks() {
        RedisService redisService = mock(RedisService.class);
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        when(redisService.get("auth:revoke:user:10000000-0000-0000-0000-000000000001"))
                .thenReturn(java.util.Optional.of("{\"revokedAtMillis\":1782950400000,\"reason\":\"LOGOUT_ALL\"}"));

        AuthService authService = new AuthService(null, redisService, null, null, null, null, null);

        assertThat(authService.findUserRevokedAtMillis(userId)).contains(1782950400000L);
    }
}

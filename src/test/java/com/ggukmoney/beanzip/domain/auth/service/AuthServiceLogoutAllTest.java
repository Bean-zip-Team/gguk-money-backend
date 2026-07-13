package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutAllResponse;
import com.ggukmoney.beanzip.global.service.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthServiceLogoutAllTest {

    @Test
    void logoutAllDeletesAllRedisSessionsAndReturnsRevokedCount() {
        RedisService redisService = mock(RedisService.class);
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        when(redisService.executeScript(
                any(RedisScript.class),
                eq(List.of(AuthService.userSessionsKey(userId), "auth:revoke:user:" + userId)),
                anyString(),
                anyString(),
                anyString(),
                eq("access-jti-1"),
                eq(String.valueOf(Instant.parse("2026-07-02T00:15:00Z").toEpochMilli()))
        )).thenReturn(3L);

        AuthService authService = new AuthService(null, redisService, null, null, null, null, null, null, null);

        LogoutAllResponse response = authService.logoutAll(
                userId,
                "access-jti-1",
                Instant.parse("2026-07-02T00:15:00Z")
        );

        assertThat(response.loggedOutAll()).isTrue();
        assertThat(response.revokedSessionCount()).isEqualTo(3);
    }
}

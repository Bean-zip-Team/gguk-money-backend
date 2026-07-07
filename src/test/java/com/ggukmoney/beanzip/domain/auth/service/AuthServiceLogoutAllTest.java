package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutAllResponse;
import com.ggukmoney.beanzip.domain.auth.infra.RedisAuthSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthServiceLogoutAllTest {

    @Test
    void logoutAllDeletesAllRedisSessionsAndReturnsRevokedCount() {
        RedisAuthSessionRepository authSessionRepository = mock(RedisAuthSessionRepository.class);
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        when(authSessionRepository.revokeAllUserSessions(
                eq(userId),
                eq("access-jti-1"),
                eq(Instant.parse("2026-07-02T00:15:00Z")),
                any(Instant.class),
                eq("LOGOUT_ALL")
        )).thenReturn(3L);

        AuthService authService = new AuthService(null, authSessionRepository, null, null, null, null, null);

        LogoutAllResponse response = authService.logoutAll(
                userId,
                "access-jti-1",
                Instant.parse("2026-07-02T00:15:00Z")
        );

        assertThat(response.loggedOutAll()).isTrue();
        assertThat(response.revokedSessionCount()).isEqualTo(3);
    }
}

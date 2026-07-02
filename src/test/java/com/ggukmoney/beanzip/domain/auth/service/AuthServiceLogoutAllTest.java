package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.audit.AuthAuditEventType;
import com.ggukmoney.beanzip.domain.auth.audit.AuthAuditResult;
import com.ggukmoney.beanzip.domain.auth.audit.AuthAuditService;
import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutAllResponse;
import com.ggukmoney.beanzip.domain.auth.infra.RedisAuthSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AuthServiceLogoutAllTest {

    @Test
    void logoutAllDeletesAllRedisSessionsAndReturnsRevokedCount() {
        RedisAuthSessionRepository authSessionRepository = mock(RedisAuthSessionRepository.class);
        AuthAuditService authAuditService = mock(AuthAuditService.class);
        when(authSessionRepository.revokeAllUserSessions(
                eq("usr_public_1"),
                eq("access-jti-1"),
                eq(Instant.parse("2026-07-02T00:15:00Z")),
                any(Instant.class),
                eq("LOGOUT_ALL")
        )).thenReturn(3L);

        AuthService authService = new AuthService(null, authSessionRepository, authAuditService);

        LogoutAllResponse response = authService.logoutAll(
                "usr_public_1",
                "access-jti-1",
                Instant.parse("2026-07-02T00:15:00Z")
        );

        assertThat(response.loggedOutAll()).isTrue();
        assertThat(response.revokedSessionCount()).isEqualTo(3);
        verify(authAuditService).record(
                eq("usr_public_1"),
                eq(null),
                eq(null),
                eq(null),
                eq(AuthAuditEventType.LOGOUT_ALL),
                eq(AuthAuditResult.SUCCESS),
                eq(null),
                eq(null)
        );
    }
}

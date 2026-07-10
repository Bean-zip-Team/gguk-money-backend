package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutAllResponse;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceLogoutAllIntegrationTest extends FullStackIntegrationTestSupport {

    @Test
    void logoutAllDeletesRealRedisSessions() {
        UUID userId = UUID.randomUUID();
        saveTokenBackedSession(userId, UUID.randomUUID().toString());
        saveTokenBackedSession(userId, UUID.randomUUID().toString());
        FullStackIntegrationTestSupport.TestTokens current = saveTokenBackedSession(userId, UUID.randomUUID().toString());

        LogoutAllResponse response = authService.logoutAll(userId, current.accessJti(), current.accessExpiresAt());

        assertThat(response.loggedOutAll()).isTrue();
        assertThat(response.revokedSessionCount()).isEqualTo(3);
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(AuthService.userSessionsKey(userId)))).isFalse();
        assertThat(redisTemplate.opsForValue().get("auth:revoke:user:" + userId)).contains("LOGOUT_ALL");
        assertThat(redisTemplate.opsForValue().get("auth:deny:access:" + current.accessJti())).isEqualTo("1");
    }

    @Test
    void logoutAllSucceedsWhenUserHasNoSessions() {
        UUID userId = UUID.randomUUID();

        LogoutAllResponse response = authService.logoutAll(userId, null, null);

        assertThat(response.loggedOutAll()).isTrue();
        assertThat(response.revokedSessionCount()).isZero();
        assertThat(redisTemplate.opsForValue().get("auth:revoke:user:" + userId)).contains("LOGOUT_ALL");
    }
}

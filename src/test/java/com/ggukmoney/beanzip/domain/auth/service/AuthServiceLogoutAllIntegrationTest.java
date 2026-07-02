package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutAllResponse;
import com.ggukmoney.beanzip.domain.auth.infra.RedisAuthSessionRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceLogoutAllIntegrationTest extends FullStackIntegrationTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private AuthService authService;

    @Test
    void logoutAllDeletesRealRedisSessionsAndPersistsAuditLog() {
        String userPublicId = UUID.randomUUID().toString();
        saveTokenBackedSession(userPublicId, UUID.randomUUID().toString());
        saveTokenBackedSession(userPublicId, UUID.randomUUID().toString());
        FullStackIntegrationTestSupport.TestTokens current = saveTokenBackedSession(userPublicId, UUID.randomUUID().toString());

        LogoutAllResponse response = authService.logoutAll(userPublicId, current.accessJti(), current.accessExpiresAt());

        assertThat(response.loggedOutAll()).isTrue();
        assertThat(response.revokedSessionCount()).isEqualTo(3);
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.userSessionsKey(userPublicId)))).isFalse();
        assertThat(redisTemplate.opsForValue().get("auth:revoke:user:" + userPublicId)).contains("LOGOUT_ALL");
        assertThat(redisTemplate.opsForValue().get("auth:deny:access:" + current.accessJti())).isEqualTo("1");
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_session_log where event_type = 'LOGOUT_ALL' and result = 'SUCCESS'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void logoutAllSucceedsWhenUserHasNoSessions() {
        String userPublicId = UUID.randomUUID().toString();

        LogoutAllResponse response = authService.logoutAll(userPublicId, null, null);

        assertThat(response.loggedOutAll()).isTrue();
        assertThat(response.revokedSessionCount()).isZero();
        assertThat(redisTemplate.opsForValue().get("auth:revoke:user:" + userPublicId)).contains("LOGOUT_ALL");
    }
}
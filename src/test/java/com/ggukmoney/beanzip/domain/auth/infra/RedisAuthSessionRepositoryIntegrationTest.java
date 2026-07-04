package com.ggukmoney.beanzip.domain.auth.infra;

import com.ggukmoney.beanzip.domain.auth.model.AuthSession;
import com.ggukmoney.beanzip.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisAuthSessionRepositoryIntegrationTest extends RedisIntegrationTestSupport {

    @Test
    void savesFindsAndDeletesRefreshSessionInRealRedis() {
        UUID sessionId = UUID.randomUUID();
        String userPublicId = UUID.randomUUID().toString();
        AuthSession session = activeSession(
                sessionId,
                userPublicId,
                UUID.randomUUID().toString(),
                "current-jti-hash",
                "refresh-token-hash",
                "family-hash",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        repository.save(session);

        String refreshKey = RedisAuthSessionRepository.refreshKey(sessionId);
        Map<Object, Object> stored = redisTemplate.opsForHash().entries(refreshKey);
        assertThat(stored).containsEntry("userPublicId", session.userPublicId())
                .containsEntry("devicePublicId", session.devicePublicId())
                .containsEntry("currentRefreshJtiHash", "current-jti-hash")
                .containsEntry("refreshTokenHash", "refresh-token-hash")
                .containsEntry("tokenFamilyIdHash", "family-hash")
                .containsEntry("previousRefreshJtiHash", "")
                .containsEntry("rotatedAt", "")
                .containsEntry("issuedAt", session.issuedAt().toString())
                .containsEntry("expiresAt", session.expiresAt().toString())
                .containsEntry("status", "ACTIVE");
        assertThat(redisTemplate.getExpire(refreshKey)).isBetween(3500L, 3600L);
        assertThat(redisTemplate.opsForZSet().score(RedisAuthSessionRepository.userSessionsKey(userPublicId), sessionId.toString()))
                .isEqualTo((double) session.expiresAt().toEpochMilli());

        assertThat(repository.findBySessionId(sessionId)).contains(session);

        repository.deleteSession(sessionId);

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(refreshKey))).isFalse();
        assertThat(redisTemplate.opsForZSet().score(RedisAuthSessionRepository.userSessionsKey(userPublicId), sessionId.toString())).isNull();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.userSessionsKey(userPublicId)))).isFalse();
    }

    @Test
    void revokeAllCountsOnlyLiveRefreshSessionsAndCleansExpiredMembers() {
        String userPublicId = UUID.randomUUID().toString();
        UUID liveSessionId = UUID.randomUUID();
        UUID missingSessionId = UUID.randomUUID();
        UUID expiredSessionId = UUID.randomUUID();
        Instant now = Instant.now();

        repository.save(activeSession(liveSessionId, userPublicId, UUID.randomUUID().toString(), "jti-live", "token-live", "family-live", now, now.plusSeconds(3600)));
        redisTemplate.opsForZSet().add(RedisAuthSessionRepository.userSessionsKey(userPublicId), missingSessionId.toString(), now.plusSeconds(3600).toEpochMilli());
        redisTemplate.opsForZSet().add(RedisAuthSessionRepository.userSessionsKey(userPublicId), expiredSessionId.toString(), now.minusSeconds(60).toEpochMilli());

        long revokedCount = repository.revokeAllUserSessions(userPublicId, "access-jti", now.plusSeconds(900), now, "LOGOUT_ALL");

        assertThat(revokedCount).isEqualTo(1);
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.refreshKey(liveSessionId)))).isFalse();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.userSessionsKey(userPublicId)))).isFalse();
        assertThat(redisTemplate.opsForValue().get("auth:revoke:user:" + userPublicId))
                .contains("\"revokedAtMillis\":" + now.toEpochMilli())
                .contains("\"reason\":\"LOGOUT_ALL\"");
        assertThat(redisTemplate.opsForValue().get("auth:deny:access:access-jti")).isEqualTo("1");
    }

    @Test
    void revokeAllStoresMarkerWhenUserHasNoSessions() {
        String userPublicId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        long revokedCount = repository.revokeAllUserSessions(userPublicId, null, null, now, "LOGOUT_ALL");

        assertThat(revokedCount).isZero();
        assertThat(redisTemplate.opsForValue().get("auth:revoke:user:" + userPublicId))
                .contains("\"revokedAtMillis\":" + now.toEpochMilli())
                .contains("\"reason\":\"LOGOUT_ALL\"");
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.userSessionsKey(userPublicId)))).isFalse();
    }
}

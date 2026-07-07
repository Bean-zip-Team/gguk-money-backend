package com.ggukmoney.beanzip.domain.auth.infra;

import com.ggukmoney.beanzip.domain.auth.model.AuthSession;
import com.ggukmoney.beanzip.domain.auth.model.RefreshRotationResult;
import com.ggukmoney.beanzip.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshLuaCasIntegrationTest extends RedisIntegrationTestSupport {

    @Test
    void rotatesRefreshTokenAtomicallyInRealRedis() {
        AuthSession session = savedSession();
        Instant rotatedAt = Instant.now();
        Instant newExpiresAt = rotatedAt.plusSeconds(7200);

        RefreshRotationResult result = repository.rotateRefreshToken(
                session,
                "jti-A",
                "token-A",
                "jti-B",
                "token-B",
                rotatedAt,
                newExpiresAt
        );

        assertThat(result).isEqualTo(RefreshRotationResult.ROTATED);
        AuthSession rotated = repository.findBySessionId(session.sessionId()).orElseThrow();
        assertThat(rotated.currentRefreshJtiHash()).isEqualTo("jti-B");
        assertThat(rotated.refreshTokenHash()).isEqualTo("token-B");
        assertThat(rotated.previousRefreshJtiHash()).isEqualTo("jti-A");
        assertThat(rotated.rotatedAt()).isEqualTo(rotatedAt);
        assertThat(rotated.expiresAt()).isEqualTo(newExpiresAt);
        assertThat(redisTemplate.getExpire(RedisAuthSessionRepository.refreshKey(session.sessionId()))).isBetween(7100L, 7200L);
        assertThat(redisTemplate.opsForZSet().score(RedisAuthSessionRepository.userSessionsKey(session.userId()), session.sessionId().toString()))
                .isEqualTo((double) newExpiresAt.toEpochMilli());
    }

    @Test
    void returnsConflictWithoutMutatingSessionWhenExpectedValuesDoNotMatch() {
        AuthSession session = savedSession();

        RefreshRotationResult result = repository.rotateRefreshToken(
                session,
                "wrong-jti",
                "token-A",
                "jti-B",
                "token-B",
                Instant.now(),
                Instant.now().plusSeconds(7200)
        );

        assertThat(result).isEqualTo(RefreshRotationResult.CONFLICT);
        AuthSession unchanged = repository.findBySessionId(session.sessionId()).orElseThrow();
        assertThat(unchanged.currentRefreshJtiHash()).isEqualTo("jti-A");
        assertThat(unchanged.refreshTokenHash()).isEqualTo("token-A");
        assertThat(unchanged.previousRefreshJtiHash()).isNull();
    }

    @Test
    void returnsNotFoundWithoutCreatingKeysWhenSessionDoesNotExist() {
        AuthSession session = activeSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString(), "jti-A", "token-A", "family-A", Instant.now(), Instant.now().plusSeconds(3600));

        RefreshRotationResult result = repository.rotateRefreshToken(
                session,
                "jti-A",
                "token-A",
                "jti-B",
                "token-B",
                Instant.now(),
                Instant.now().plusSeconds(7200)
        );

        assertThat(result).isEqualTo(RefreshRotationResult.NOT_FOUND);
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.refreshKey(session.sessionId())))).isFalse();
    }

    @Test
    void allowsOnlyOneConcurrentRotationAndKeepsSessionActive() throws Exception {
        AuthSession session = savedSession();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<RefreshRotationResult> rotation = () -> {
            ready.countDown();
            start.await();
            return repository.rotateRefreshToken(
                    session,
                    "jti-A",
                    "token-A",
                    "jti-B-" + UUID.randomUUID(),
                    "token-B-" + UUID.randomUUID(),
                    Instant.now(),
                    Instant.now().plusSeconds(7200)
            );
        };

        Future<RefreshRotationResult> first = executorService.submit(rotation);
        Future<RefreshRotationResult> second = executorService.submit(rotation);
        ready.await();
        start.countDown();
        List<RefreshRotationResult> results = List.of(first.get(), second.get());
        executorService.shutdownNow();

        assertThat(results).containsExactlyInAnyOrder(RefreshRotationResult.ROTATED, RefreshRotationResult.CONFLICT);
        assertThat(repository.findBySessionId(session.sessionId())).isPresent();
    }

    @Test
    void treatsPreviousRefreshAfterConflictWindowAsReuse() {
        AuthSession session = savedSession();
        Instant firstRotationAt = Instant.now();
        repository.rotateRefreshToken(session, "jti-A", "token-A", "jti-B", "token-B", firstRotationAt, firstRotationAt.plusSeconds(7200));

        RefreshRotationResult result = repository.rotateRefreshToken(
                session,
                "jti-A",
                "token-A",
                "jti-C",
                "token-C",
                firstRotationAt.plusSeconds(3),
                firstRotationAt.plusSeconds(9000)
        );

        assertThat(result).isEqualTo(RefreshRotationResult.REUSED);
    }

    private AuthSession savedSession() {
        AuthSession session = activeSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "jti-A",
                "token-A",
                "family-A",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        repository.save(session);
        return session;
    }
}

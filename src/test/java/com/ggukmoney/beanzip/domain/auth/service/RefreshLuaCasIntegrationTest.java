package com.ggukmoney.beanzip.domain.auth.service;

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
        AuthService.AuthSession session = savedSession();
        Instant rotatedAt = Instant.now();
        Instant newExpiresAt = rotatedAt.plusSeconds(7200);

        AuthService.RefreshRotationResult result = authService.rotateRefreshToken(
                session,
                "jti-A",
                "token-A",
                "jti-B",
                "token-B",
                rotatedAt,
                newExpiresAt
        );

        assertThat(result).isEqualTo(AuthService.RefreshRotationResult.ROTATED);
        AuthService.AuthSession rotated = authService.findBySessionId(session.sessionId()).orElseThrow();
        assertThat(rotated.currentRefreshJtiHash()).isEqualTo("jti-B");
        assertThat(rotated.refreshTokenHash()).isEqualTo("token-B");
        assertThat(rotated.previousRefreshJtiHash()).isEqualTo("jti-A");
        assertThat(rotated.rotatedAt()).isEqualTo(rotatedAt);
        assertThat(rotated.expiresAt()).isEqualTo(newExpiresAt);
        assertThat(redisTemplate.getExpire(AuthService.refreshKey(session.sessionId()))).isBetween(7100L, 7200L);
        assertThat(redisTemplate.opsForZSet().score(AuthService.userSessionsKey(session.userId()), session.sessionId().toString()))
                .isEqualTo((double) newExpiresAt.toEpochMilli());
    }

    @Test
    // 현재도 직전도 아닌 토큰은 유효해질 수 없으므로 409(재시도)가 아니라 재사용으로 판정해야 앱이 세션을 정리한다.
    void treatsUnknownRefreshTokenAsReuseWithoutMutatingSession() {
        AuthService.AuthSession session = savedSession();

        AuthService.RefreshRotationResult result = authService.rotateRefreshToken(
                session,
                "wrong-jti",
                "token-A",
                "jti-B",
                "token-B",
                Instant.now(),
                Instant.now().plusSeconds(7200)
        );

        assertThat(result).isEqualTo(AuthService.RefreshRotationResult.REUSED);
        AuthService.AuthSession unchanged = authService.findBySessionId(session.sessionId()).orElseThrow();
        assertThat(unchanged.currentRefreshJtiHash()).isEqualTo("jti-A");
        assertThat(unchanged.refreshTokenHash()).isEqualTo("token-A");
        assertThat(unchanged.previousRefreshJtiHash()).isNull();
    }

    @Test
    void returnsNotFoundWithoutCreatingKeysWhenSessionDoesNotExist() {
        AuthService.AuthSession session = activeSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString(), "jti-A", "token-A", "family-A", Instant.now(), Instant.now().plusSeconds(3600));

        AuthService.RefreshRotationResult result = authService.rotateRefreshToken(
                session,
                "jti-A",
                "token-A",
                "jti-B",
                "token-B",
                Instant.now(),
                Instant.now().plusSeconds(7200)
        );

        assertThat(result).isEqualTo(AuthService.RefreshRotationResult.NOT_FOUND);
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(AuthService.refreshKey(session.sessionId())))).isFalse();
    }

    @Test
    void allowsOnlyOneConcurrentRotationAndKeepsSessionActive() throws Exception {
        AuthService.AuthSession session = savedSession();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<AuthService.RefreshRotationResult> rotation = () -> {
            ready.countDown();
            start.await();
            return authService.rotateRefreshToken(
                    session,
                    "jti-A",
                    "token-A",
                    "jti-B-" + UUID.randomUUID(),
                    "token-B-" + UUID.randomUUID(),
                    Instant.now(),
                    Instant.now().plusSeconds(7200)
            );
        };

        Future<AuthService.RefreshRotationResult> first = executorService.submit(rotation);
        Future<AuthService.RefreshRotationResult> second = executorService.submit(rotation);
        ready.await();
        start.countDown();
        List<AuthService.RefreshRotationResult> results = List.of(first.get(), second.get());
        executorService.shutdownNow();

        assertThat(results).containsExactlyInAnyOrder(AuthService.RefreshRotationResult.ROTATED, AuthService.RefreshRotationResult.CONFLICT);
        assertThat(authService.findBySessionId(session.sessionId())).isPresent();
    }

    @Test
    void treatsPreviousRefreshAfterConflictWindowAsReuse() {
        AuthService.AuthSession session = savedSession();
        Instant firstRotationAt = Instant.now();
        authService.rotateRefreshToken(session, "jti-A", "token-A", "jti-B", "token-B", firstRotationAt, firstRotationAt.plusSeconds(7200));

        AuthService.RefreshRotationResult result = authService.rotateRefreshToken(
                session,
                "jti-A",
                "token-A",
                "jti-C",
                "token-C",
                firstRotationAt.plusSeconds(3),
                firstRotationAt.plusSeconds(9000)
        );

        assertThat(result).isEqualTo(AuthService.RefreshRotationResult.REUSED);
    }

    // 저장을 두 번 놓친 앱이 직전보다 오래된 토큰을 내는 경우. 유예 안이어도 재사용이며 스크립트는 세션을 건드리지 않는다.
    @Test
    void treatsRefreshTokenOlderThanPreviousAsReuseEvenInsideConflictWindow() {
        AuthService.AuthSession session = savedSession();
        Instant firstRotationAt = Instant.now();
        authService.rotateRefreshToken(session, "jti-A", "token-A", "jti-B", "token-B", firstRotationAt, firstRotationAt.plusSeconds(7200));
        Instant secondRotationAt = firstRotationAt.plusSeconds(1);
        authService.rotateRefreshToken(session, "jti-B", "token-B", "jti-C", "token-C", secondRotationAt, secondRotationAt.plusSeconds(7200));

        AuthService.RefreshRotationResult result = authService.rotateRefreshToken(
                session,
                "jti-A",
                "token-A",
                "jti-D",
                "token-D",
                secondRotationAt.plusMillis(500),
                secondRotationAt.plusSeconds(9000)
        );

        assertThat(result).isEqualTo(AuthService.RefreshRotationResult.REUSED);
        AuthService.AuthSession unchanged = authService.findBySessionId(session.sessionId()).orElseThrow();
        assertThat(unchanged.currentRefreshJtiHash()).isEqualTo("jti-C");
        assertThat(unchanged.previousRefreshJtiHash()).isEqualTo("jti-B");
    }

    private AuthService.AuthSession savedSession() {
        AuthService.AuthSession session = activeSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "jti-A",
                "token-A",
                "family-A",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        authService.save(session);
        return session;
    }
}

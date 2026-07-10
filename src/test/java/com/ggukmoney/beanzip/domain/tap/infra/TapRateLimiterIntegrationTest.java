package com.ggukmoney.beanzip.domain.tap.infra;

import com.ggukmoney.beanzip.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TapRateLimiterIntegrationTest extends RedisIntegrationTestSupport {

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        TapRateLimiter rateLimiter = new TapRateLimiter(redisTemplate);
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryConsume(userId, 3, 0.1)).isTrue();
        }

        assertThat(rateLimiter.tryConsume(userId, 3, 0.1)).isFalse();
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        TapRateLimiter rateLimiter = new TapRateLimiter(redisTemplate);
        UUID userId = UUID.randomUUID();

        assertThat(rateLimiter.tryConsume(userId, 1, 5.0)).isTrue();
        assertThat(rateLimiter.tryConsume(userId, 1, 5.0)).isFalse();

        Thread.sleep(300);

        assertThat(rateLimiter.tryConsume(userId, 1, 5.0)).isTrue();
    }

    @Test
    void bucketsAreIndependentPerUser() {
        TapRateLimiter rateLimiter = new TapRateLimiter(redisTemplate);
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        assertThat(rateLimiter.tryConsume(userA, 1, 0.1)).isTrue();
        assertThat(rateLimiter.tryConsume(userA, 1, 0.1)).isFalse();
        assertThat(rateLimiter.tryConsume(userB, 1, 0.1)).isTrue();
    }

    @Test
    void minuteCounterAccumulates() {
        TapRateLimiter rateLimiter = new TapRateLimiter(redisTemplate);
        UUID userId = UUID.randomUUID();

        assertThat(rateLimiter.getMinuteCount(userId)).isZero();

        rateLimiter.addMinuteCount(userId, 40);
        rateLimiter.addMinuteCount(userId, 5);

        assertThat(rateLimiter.getMinuteCount(userId)).isEqualTo(45);
    }

    @Test
    void minuteCounterIgnoresNonPositiveDelta() {
        TapRateLimiter rateLimiter = new TapRateLimiter(redisTemplate);
        UUID userId = UUID.randomUUID();

        rateLimiter.addMinuteCount(userId, 0);
        rateLimiter.addMinuteCount(userId, -5);

        assertThat(rateLimiter.getMinuteCount(userId)).isZero();
    }
}

package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TapBatchServiceRateLimitIntegrationTest extends RedisIntegrationTestSupport {

    private final Instant now = Instant.parse("2026-07-20T15:00:00Z");

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            assertThat(tapBatchService.tryConsumeRateLimit(userId, 3, 0.1, now)).isTrue();
        }

        assertThat(tapBatchService.tryConsumeRateLimit(userId, 3, 0.1, now)).isFalse();
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        UUID userId = UUID.randomUUID();

        assertThat(tapBatchService.tryConsumeRateLimit(userId, 1, 5.0, now)).isTrue();
        assertThat(tapBatchService.tryConsumeRateLimit(userId, 1, 5.0, now)).isFalse();

        Thread.sleep(300);

        assertThat(tapBatchService.tryConsumeRateLimit(userId, 1, 5.0, now.plusMillis(300))).isTrue();
    }

    @Test
    void bucketsAreIndependentPerUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        assertThat(tapBatchService.tryConsumeRateLimit(userA, 1, 0.1, now)).isTrue();
        assertThat(tapBatchService.tryConsumeRateLimit(userA, 1, 0.1, now)).isFalse();
        assertThat(tapBatchService.tryConsumeRateLimit(userB, 1, 0.1, now)).isTrue();
    }

    @Test
    void minuteCounterAccumulates() {
        UUID userId = UUID.randomUUID();

        assertThat(tapBatchService.getMinuteCount(userId)).isZero();

        tapBatchService.addMinuteCount(userId, 40);
        tapBatchService.addMinuteCount(userId, 5);

        assertThat(tapBatchService.getMinuteCount(userId)).isEqualTo(45);
    }

    @Test
    void minuteCounterIgnoresNonPositiveDelta() {
        UUID userId = UUID.randomUUID();

        tapBatchService.addMinuteCount(userId, 0);
        tapBatchService.addMinuteCount(userId, -5);

        assertThat(tapBatchService.getMinuteCount(userId)).isZero();
    }
}

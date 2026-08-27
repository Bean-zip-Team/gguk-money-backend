package com.ggukmoney.beanzip.domain.keycap.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycapBoxAccountTest {

    private static final Duration ONE_HOUR = Duration.ofHours(1);

    @Test
    void createsAccountWithOpenCycleStartedAtFromCreationTime() {
        Instant createdAt = Instant.parse("2026-07-16T00:00:00Z");

        KeycapBoxAccount account = KeycapBoxAccount.createFor(null, createdAt);

        assertThat(account.getOpenCycleStartedAt()).isEqualTo(createdAt);
        assertThat(account.getLastFreeTicketGrantedAt()).isEqualTo(createdAt);
    }

    @Test
    void calculatesSnapshotWithoutChangingEntityState() {
        Instant cycleStartedAt = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithCycle(2, 1, 2, cycleStartedAt);

        KeycapBoxAccount.OpenCycleSnapshot snapshot = account.calculateOpenCycleSnapshot(
                cycleStartedAt.plusSeconds(10 * 60),
                ONE_HOUR,
                2,
                2
        );

        assertThat(snapshot.canFreeOpen()).isTrue();
        assertThat(snapshot.canAdOpen()).isFalse();
        assertThat(snapshot.charging()).isFalse();
        assertThat(snapshot.nextRechargeAt()).isNull();
        assertThat(account.getFreeOpenUsedCount()).isEqualTo(1);
        assertThat(account.getAdOpenUsedCount()).isEqualTo(2);
        assertThat(account.getOpenCycleStartedAt()).isEqualTo(cycleStartedAt);
    }

    @Test
    void reportsChargingWhenBothLimitsAreUsedEvenWithoutBox() {
        Instant cycleStartedAt = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithCycle(0, 2, 2, cycleStartedAt);

        KeycapBoxAccount.OpenCycleSnapshot snapshot = account.calculateOpenCycleSnapshot(
                cycleStartedAt.plusSeconds(10),
                ONE_HOUR,
                2,
                2
        );

        assertThat(snapshot.canFreeOpen()).isFalse();
        assertThat(snapshot.canAdOpen()).isFalse();
        assertThat(snapshot.charging()).isTrue();
        assertThat(snapshot.nextRechargeAt()).isEqualTo(cycleStartedAt.plus(ONE_HOUR));
    }

    @Test
    void doesNotReportChargingWhenBoxIsMissingButOpenQuotaRemains() {
        Instant cycleStartedAt = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithCycle(0, 1, 2, cycleStartedAt);

        KeycapBoxAccount.OpenCycleSnapshot snapshot = account.calculateOpenCycleSnapshot(
                cycleStartedAt.plusSeconds(10),
                ONE_HOUR,
                2,
                2
        );

        assertThat(snapshot.canFreeOpen()).isFalse();
        assertThat(snapshot.canAdOpen()).isFalse();
        assertThat(snapshot.charging()).isFalse();
        assertThat(snapshot.nextRechargeAt()).isNull();
    }

    @Test
    void reportsChargingWhenBothLimitsAreUsedAndBoxExists() {
        Instant cycleStartedAt = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithCycle(1, 2, 2, cycleStartedAt);

        KeycapBoxAccount.OpenCycleSnapshot snapshot = account.calculateOpenCycleSnapshot(
                cycleStartedAt.plusSeconds(10),
                ONE_HOUR,
                2,
                2
        );

        assertThat(snapshot.canFreeOpen()).isFalse();
        assertThat(snapshot.canAdOpen()).isFalse();
        assertThat(snapshot.charging()).isTrue();
        assertThat(snapshot.nextRechargeAt()).isEqualTo(cycleStartedAt.plus(ONE_HOUR));
    }

    @Test
    void refreshesOpenCycleByElapsedCycleCountFromExistingStart() {
        Instant cycleStartedAt = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithCycle(1, 2, 2, cycleStartedAt);

        account.refreshOpenCycle(cycleStartedAt.plusSeconds(2 * 3600 + 60), ONE_HOUR);

        assertThat(account.getOpenCycleStartedAt()).isEqualTo(cycleStartedAt.plusSeconds(2 * 3600));
        assertThat(account.getFreeOpenUsedCount()).isZero();
        assertThat(account.getAdOpenUsedCount()).isZero();
    }

    @Test
    void resetsAdvertisementQuotaBySharedCycleWithoutADailyLimit() {
        Instant cycleStartedAt = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithCycle(3, 0, 2, cycleStartedAt);

        account.refreshOpenCycle(cycleStartedAt.plus(ONE_HOUR), ONE_HOUR);
        account.consumeAdOpen(2);

        assertThat(account.getBoxBalance()).isEqualTo(2);
        assertThat(account.getAdOpenUsedCount()).isEqualTo(1);
        assertThat(account.getOpenCycleStartedAt()).isEqualTo(cycleStartedAt.plus(ONE_HOUR));
    }

    @Test
    void calculatesEffectiveOpenCycleStartWithoutChangingState() {
        Instant cycleStartedAt = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithCycle(1, 2, 2, cycleStartedAt);

        Instant effectiveCycleStartedAt = account.calculateEffectiveOpenCycleStartedAt(
                cycleStartedAt.plusSeconds(2 * 3600 + 60),
                ONE_HOUR
        );

        assertThat(effectiveCycleStartedAt).isEqualTo(cycleStartedAt.plusSeconds(2 * 3600));
        assertThat(account.getOpenCycleStartedAt()).isEqualTo(cycleStartedAt);
        assertThat(account.getFreeOpenUsedCount()).isEqualTo(2);
        assertThat(account.getAdOpenUsedCount()).isEqualTo(2);
    }

    @Test
    void doesNotMoveOpenCycleBackwardWhenNowIsBeforeCycleStartedAt() {
        Instant cycleStartedAt = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithCycle(1, 1, 1, cycleStartedAt);

        account.refreshOpenCycle(cycleStartedAt.minusSeconds(60), ONE_HOUR);

        assertThat(account.getOpenCycleStartedAt()).isEqualTo(cycleStartedAt);
        assertThat(account.getFreeOpenUsedCount()).isEqualTo(1);
        assertThat(account.getAdOpenUsedCount()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidOpenCyclePolicyValues() {
        KeycapBoxAccount account = accountWithCycle(1, 0, 0, Instant.parse("2026-07-16T00:00:00Z"));

        assertThatThrownBy(() -> account.calculateOpenCycleSnapshot(Instant.parse("2026-07-16T00:00:00Z"), Duration.ZERO, 2, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.calculateOpenCycleSnapshot(Instant.parse("2026-07-16T00:00:00Z"), ONE_HOUR, -1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.calculateOpenCycleSnapshot(Instant.parse("2026-07-16T00:00:00Z"), ONE_HOUR, 2, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverflowedOpenCycleCalculation() {
        KeycapBoxAccount account = accountWithCycle(1, 0, 0, Instant.EPOCH);

        assertThatThrownBy(() -> account.refreshOpenCycle(Instant.MAX, Duration.ofNanos(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsSixAdvertisementOpensWithinCycle() {
        KeycapBoxAccount account = accountWithCycle(6, 0, 0, Instant.parse("2026-07-16T00:00:00Z"));

        for (int attempt = 1; attempt <= 6; attempt++) {
            account.consumeAdOpen(6);
        }

        assertThat(account.getBoxBalance()).isZero();
        assertThat(account.getFreeOpenUsedCount()).isZero();
        assertThat(account.getAdOpenUsedCount()).isEqualTo(6);
    }

    @Test
    void rejectsSeventhAdvertisementOpenWithoutConsumingBox() {
        KeycapBoxAccount account = accountWithCycle(1, 0, 6, Instant.parse("2026-07-16T00:00:00Z"));

        assertThatThrownBy(() -> account.consumeAdOpen(6))
                .isInstanceOf(IllegalStateException.class);

        assertThat(account.getBoxBalance()).isEqualTo(1);
        assertThat(account.getAdOpenUsedCount()).isEqualTo(6);
    }

    @Test
    void rejectsSharedCycleLimitWithoutConsumingBox() {
        KeycapBoxAccount account = accountWithCycle(1, 2, 0, Instant.parse("2026-07-16T00:00:00Z"));

        assertThatThrownBy(() -> account.consumeFreeOpen(2))
                .isInstanceOf(IllegalStateException.class);

        assertThat(account.getBoxBalance()).isEqualTo(1);
        assertThat(account.getFreeOpenUsedCount()).isEqualTo(2);
    }

    private static KeycapBoxAccount accountWithCycle(
            int boxBalance,
            int freeOpenUsedCount,
            int adOpenUsedCount,
            Instant openCycleStartedAt
    ) {
        KeycapBoxAccount account = new KeycapBoxAccount();
        ReflectionTestUtils.setField(account, "boxBalance", boxBalance);
        ReflectionTestUtils.setField(account, "freeOpenUsedCount", freeOpenUsedCount);
        ReflectionTestUtils.setField(account, "adOpenUsedCount", adOpenUsedCount);
        ReflectionTestUtils.setField(account, "openCycleStartedAt", openCycleStartedAt);
        return account;
    }
}

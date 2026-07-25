package com.ggukmoney.beanzip.domain.keycap.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;

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
    void distinguishesMissingBoxFromChargingState() {
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
        assertThat(snapshot.charging()).isFalse();
        assertThat(snapshot.nextRechargeAt()).isNull();
    }

    @Test
    void reportsChargingOnlyWhenBothLimitsAreUsedAndBoxExists() {
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
    void consumesFreeAndAdOpenAgainstSharedCycleLimits() {
        KeycapBoxAccount account = accountWithCycle(4, 0, 0, Instant.parse("2026-07-16T00:00:00Z"));

        account.consumeFreeOpen(2);
        account.consumeAdOpen(2);
        account.consumeFreeOpen(2);
        account.consumeAdOpen(2);

        assertThat(account.getBoxBalance()).isZero();
        assertThat(account.getFreeOpenUsedCount()).isEqualTo(2);
        assertThat(account.getAdOpenUsedCount()).isEqualTo(2);
    }

    @Test
    void rejectsSharedCycleLimitWithoutConsumingBox() {
        KeycapBoxAccount account = accountWithCycle(1, 2, 0, Instant.parse("2026-07-16T00:00:00Z"));

        assertThatThrownBy(() -> account.consumeFreeOpen(2))
                .isInstanceOf(IllegalStateException.class);

        assertThat(account.getBoxBalance()).isEqualTo(1);
        assertThat(account.getFreeOpenUsedCount()).isEqualTo(2);
    }

    @Test
    void consumesFreeOpenResources() {
        KeycapBoxAccount account = account(2, 1);

        account.consumeFreeOpen();

        assertThat(account.getBoxBalance()).isEqualTo(1);
        assertThat(account.getFreeOpenTicketCount()).isZero();
    }

    @Test
    void rejectsFreeOpenWhenBoxBalanceIsMissing() {
        KeycapBoxAccount account = account(0, 1);

        assertThatThrownBy(account::consumeFreeOpen)
                .isInstanceOf(IllegalStateException.class);
        assertThat(account.getBoxBalance()).isZero();
        assertThat(account.getFreeOpenTicketCount()).isEqualTo(1);
    }

    @Test
    void rejectsFreeOpenWhenTicketIsMissing() {
        KeycapBoxAccount account = account(1, 0);

        assertThatThrownBy(account::consumeFreeOpen)
                .isInstanceOf(IllegalStateException.class);
        assertThat(account.getBoxBalance()).isEqualTo(1);
        assertThat(account.getFreeOpenTicketCount()).isZero();
    }

    @Test
    void grantsOneTicketPerElapsedHourUpToCap() {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithTicket(0, start);

        account.grantElapsedFreeTickets(start.plusSeconds(3 * 3600), 1, 8);

        assertThat(account.getFreeOpenTicketCount()).isEqualTo(3);
        assertThat(account.getLastFreeTicketGrantedAt()).isEqualTo(start.plusSeconds(3 * 3600));
    }

    @Test
    void grantsNothingWhenLessThanOneHourElapsed() {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithTicket(0, start);

        account.grantElapsedFreeTickets(start.plusSeconds(1800), 1, 8);

        assertThat(account.getFreeOpenTicketCount()).isZero();
        assertThat(account.getLastFreeTicketGrantedAt()).isEqualTo(start);
    }

    @Test
    void clampsGrantAtCapButStillAdvancesClock() {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithTicket(6, start);

        account.grantElapsedFreeTickets(start.plusSeconds(5 * 3600), 1, 8);

        assertThat(account.getFreeOpenTicketCount()).isEqualTo(8);
        assertThat(account.getLastFreeTicketGrantedAt()).isEqualTo(start.plusSeconds(5 * 3600));
    }

    @Test
    void doesNotGrantAdditionalTicketsWhenAlreadyAtCapButClockStillAdvances() {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        KeycapBoxAccount account = accountWithTicket(8, start);

        account.grantElapsedFreeTickets(start.plusSeconds(10 * 3600), 1, 8);

        assertThat(account.getFreeOpenTicketCount()).isEqualTo(8);
        assertThat(account.getLastFreeTicketGrantedAt()).isEqualTo(start.plusSeconds(10 * 3600));
    }

    @Test
    void consumesAdOpenAndDecrementsBoxBalanceWithoutTouchingFreeTickets() {
        KeycapBoxAccount account = account(2, 1);

        account.consumeAdOpen(LocalDate.parse("2026-07-16"), 2);

        assertThat(account.getBoxBalance()).isEqualTo(1);
        assertThat(account.getFreeOpenTicketCount()).isEqualTo(1);
        assertThat(account.getAdOpenCount()).isEqualTo(1);
    }

    @Test
    void resetsAdOpenCountOnNewDay() {
        KeycapBoxAccount account = account(1, 0);
        ReflectionTestUtils.setField(account, "adOpenCount", 2);
        ReflectionTestUtils.setField(account, "adOpenCountDate", LocalDate.parse("2026-07-15"));

        account.consumeAdOpen(LocalDate.parse("2026-07-16"), 2);

        assertThat(account.getAdOpenCount()).isEqualTo(1);
    }

    @Test
    void rejectsAdOpenWhenDailyLimitReached() {
        KeycapBoxAccount account = account(1, 0);
        LocalDate today = LocalDate.parse("2026-07-16");
        ReflectionTestUtils.setField(account, "adOpenCount", 2);
        ReflectionTestUtils.setField(account, "adOpenCountDate", today);

        assertThatThrownBy(() -> account.consumeAdOpen(today, 2))
                .isInstanceOf(IllegalStateException.class);
        assertThat(account.getBoxBalance()).isEqualTo(1);
        assertThat(account.getAdOpenCount()).isEqualTo(2);
    }

    @Test
    void rejectsAdOpenWhenBoxBalanceIsMissing() {
        KeycapBoxAccount account = account(0, 0);

        assertThatThrownBy(() -> account.consumeAdOpen(LocalDate.parse("2026-07-16"), 2))
                .isInstanceOf(IllegalStateException.class);
        assertThat(account.getAdOpenCount()).isZero();
    }

    private static KeycapBoxAccount account(int boxBalance, int freeOpenTicketCount) {
        KeycapBoxAccount account = new KeycapBoxAccount();
        ReflectionTestUtils.setField(account, "boxBalance", boxBalance);
        ReflectionTestUtils.setField(account, "freeOpenTicketCount", freeOpenTicketCount);
        return account;
    }

    private static KeycapBoxAccount accountWithTicket(int freeOpenTicketCount, Instant lastGrantedAt) {
        KeycapBoxAccount account = new KeycapBoxAccount();
        ReflectionTestUtils.setField(account, "freeOpenTicketCount", freeOpenTicketCount);
        ReflectionTestUtils.setField(account, "lastFreeTicketGrantedAt", lastGrantedAt);
        return account;
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

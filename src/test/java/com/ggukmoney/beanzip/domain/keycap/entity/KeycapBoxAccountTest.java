package com.ggukmoney.beanzip.domain.keycap.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycapBoxAccountTest {

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
}

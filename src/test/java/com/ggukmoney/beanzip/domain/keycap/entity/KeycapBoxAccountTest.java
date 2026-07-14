package com.ggukmoney.beanzip.domain.keycap.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    private static KeycapBoxAccount account(int boxBalance, int freeOpenTicketCount) {
        KeycapBoxAccount account = new KeycapBoxAccount();
        ReflectionTestUtils.setField(account, "boxBalance", boxBalance);
        ReflectionTestUtils.setField(account, "freeOpenTicketCount", freeOpenTicketCount);
        return account;
    }
}

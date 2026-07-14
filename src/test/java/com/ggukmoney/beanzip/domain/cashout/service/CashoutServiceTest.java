package com.ggukmoney.beanzip.domain.cashout.service;

import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutQuoteResponse;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.global.config.CashoutPolicyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CashoutServiceTest {

    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final CashoutPolicyConfig cashoutPolicyConfig = mock(CashoutPolicyConfig.class);
    private final CashoutService cashoutService = new CashoutService(pointAccountService, cashoutPolicyConfig);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubCashoutPolicyDefaults() {
        when(cashoutPolicyConfig.minimumPoint()).thenReturn(10);
        when(cashoutPolicyConfig.pointToKrwRate()).thenReturn(0.7);
    }

    @Test
    void floorsTossPointAmountForFigmaVerifiedExample() {
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        CashoutQuoteResponse response = cashoutService.getQuote(userId);

        assertThat(response.pointBalance()).isEqualTo(134L);
        assertThat(response.tossPointAmount()).isEqualTo(93L);
        assertThat(response.eligible()).isTrue();
        assertThat(response.minimumPoint()).isEqualTo(10);
        assertThat(response.rate().pointToKrw()).isEqualTo(0.7);
    }

    @Test
    void marksIneligibleBelowMinimumPointForFigmaVerifiedExample() {
        when(pointAccountService.getBalance(userId)).thenReturn(7L);

        CashoutQuoteResponse response = cashoutService.getQuote(userId);

        assertThat(response.tossPointAmount()).isEqualTo(4L);
        assertThat(response.eligible()).isFalse();
    }

    @Test
    void marksEligibleAtExactlyMinimumPoint() {
        when(pointAccountService.getBalance(userId)).thenReturn(10L);

        CashoutQuoteResponse response = cashoutService.getQuote(userId);

        assertThat(response.eligible()).isTrue();
    }

    @Test
    void marksIneligibleJustBelowMinimumPoint() {
        when(pointAccountService.getBalance(userId)).thenReturn(9L);

        CashoutQuoteResponse response = cashoutService.getQuote(userId);

        assertThat(response.eligible()).isFalse();
    }

    @Test
    void reflectsConfiguredMinimumAndRateWhenChanged() {
        when(cashoutPolicyConfig.minimumPoint()).thenReturn(50);
        when(cashoutPolicyConfig.pointToKrwRate()).thenReturn(0.5);
        when(pointAccountService.getBalance(userId)).thenReturn(100L);

        CashoutQuoteResponse response = cashoutService.getQuote(userId);

        assertThat(response.minimumPoint()).isEqualTo(50);
        assertThat(response.rate().pointToKrw()).isEqualTo(0.5);
        assertThat(response.tossPointAmount()).isEqualTo(50L);
    }
}

package com.ggukmoney.beanzip.domain.cashout.service;

import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutQuoteResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutSubmitResponse;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.cashout.repository.CashoutRequestRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.CashoutPolicyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashoutServiceTest {

    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
    private final CashoutRequestRepository cashoutRequestRepository = mock(CashoutRequestRepository.class);
    private final UserService userService = mock(UserService.class);
    private final CashoutPolicyConfig cashoutPolicyConfig = mock(CashoutPolicyConfig.class);
    private final CashoutService cashoutService = new CashoutService(
            pointAccountService, pointLedgerService, cashoutRequestRepository, userService, cashoutPolicyConfig);

    private final UUID userId = UUID.randomUUID();
    private final UUID idempotencyKey = UUID.randomUUID();

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

    @Test
    void submitsFullBalanceAndCreatesRequestedCashout() {
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        AppUser user = mock(AppUser.class);
        when(userService.getById(userId)).thenReturn(user);

        PointAccount account = mock(PointAccount.class);
        when(pointAccountService.debit(userId, 134L)).thenReturn(account);

        when(cashoutRequestRepository.save(any(CashoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashoutSubmitResponse response = cashoutService.submit(userId, idempotencyKey);

        assertThat(response.pointAmount()).isEqualTo(134L);
        assertThat(response.tossPointAmount()).isEqualTo(93L);
        assertThat(response.status()).isEqualTo("REQUESTED");
        verify(pointLedgerService).recordDebit(account, user, 134L, "CASHOUT", idempotencyKey);
    }

    @Test
    void throwsMinimumNotMetWhenBalanceBelowMinimum() {
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(9L);

        assertThatThrownBy(() -> cashoutService.submit(userId, idempotencyKey))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CASHOUT_MINIMUM_NOT_MET");

        verify(pointAccountService, never()).debit(any(), anyLong());
    }

    @Test
    void throwsAlreadyProcessingWhenActiveRequestExists() {
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(
                userId, List.of(CashoutRequest.Status.REQUESTED, CashoutRequest.Status.PROCESSING)))
                .thenReturn(true);

        assertThatThrownBy(() -> cashoutService.submit(userId, idempotencyKey))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CASHOUT_ALREADY_PROCESSING");

        verify(pointAccountService, never()).debit(any(), anyLong());
    }

    @Test
    void replaysExistingResponseForSameIdempotencyKeyWithoutRedebiting() {
        AppUser user = mock(AppUser.class);
        CashoutRequest existingRequest = CashoutRequest.createFor(user, 134L, 93L, idempotencyKey);
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.of(existingRequest));

        CashoutSubmitResponse response = cashoutService.submit(userId, idempotencyKey);

        assertThat(response.pointAmount()).isEqualTo(134L);
        assertThat(response.tossPointAmount()).isEqualTo(93L);
        verify(pointAccountService, never()).debit(any(), anyLong());
        verify(cashoutRequestRepository, never()).save(any());
    }
}

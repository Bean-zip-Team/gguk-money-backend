package com.ggukmoney.beanzip.domain.cashout.scheduler;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.cashout.client.TossPromotionClient;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.cashout.repository.CashoutRequestRepository;
import com.ggukmoney.beanzip.domain.cashout.service.CashoutService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashoutProcessingSchedulerTest {

    private final CashoutRequestRepository cashoutRequestRepository = mock(CashoutRequestRepository.class);
    private final AuthIdentityRepository authIdentityRepository = mock(AuthIdentityRepository.class);
    private final TossPromotionClient tossPromotionClient = mock(TossPromotionClient.class);
    private final CashoutService cashoutService = mock(CashoutService.class);
    private final CashoutProcessingScheduler scheduler =
            new CashoutProcessingScheduler(cashoutRequestRepository, authIdentityRepository, tossPromotionClient, cashoutService);

    {
        ReflectionTestUtils.setField(scheduler, "promotionCode", "TEST_PROMO");
    }

    @Test
    void finalizesEachProcessingRequestWithItsExecutionResult() {
        CashoutRequest request1 = cashoutRequestFixture("key-1");
        CashoutRequest request2 = cashoutRequestFixture("key-2");
        when(cashoutRequestRepository.findByStatusAndTossPromotionKeyIsNotNull(CashoutRequest.Status.PROCESSING))
                .thenReturn(List.of(request1, request2));
        when(tossPromotionClient.getExecutionResult("toss-user-key-key-1", "TEST_PROMO", "key-1"))
                .thenReturn(TossPromotionClient.PromotionResultStatus.SUCCESS);
        when(tossPromotionClient.getExecutionResult("toss-user-key-key-2", "TEST_PROMO", "key-2"))
                .thenReturn(TossPromotionClient.PromotionResultStatus.PENDING);

        scheduler.pollProcessingCashouts();

        verify(cashoutService).finalizeProcessingCashout(request1, TossPromotionClient.PromotionResultStatus.SUCCESS);
        verify(cashoutService).finalizeProcessingCashout(request2, TossPromotionClient.PromotionResultStatus.PENDING);
    }

    @Test
    void continuesPollingRemainingRequestsWhenOneLookupFails() {
        CashoutRequest request1 = cashoutRequestFixture("key-1");
        CashoutRequest request2 = cashoutRequestFixture("key-2");
        when(cashoutRequestRepository.findByStatusAndTossPromotionKeyIsNotNull(CashoutRequest.Status.PROCESSING))
                .thenReturn(List.of(request1, request2));
        when(tossPromotionClient.getExecutionResult("toss-user-key-key-1", "TEST_PROMO", "key-1"))
                .thenThrow(new RuntimeException("network error"));
        when(tossPromotionClient.getExecutionResult("toss-user-key-key-2", "TEST_PROMO", "key-2"))
                .thenReturn(TossPromotionClient.PromotionResultStatus.SUCCESS);

        scheduler.pollProcessingCashouts();

        verify(cashoutService, never()).finalizeProcessingCashout(eq(request1), any());
        verify(cashoutService).finalizeProcessingCashout(request2, TossPromotionClient.PromotionResultStatus.SUCCESS);
    }

    private CashoutRequest cashoutRequestFixture(String tossPromotionKey) {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        AuthIdentity identity = AuthIdentity.toss(user, "toss-user-key-" + tossPromotionKey);
        when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS))
                .thenReturn(Optional.of(identity));

        CashoutRequest request = CashoutRequest.createFor(user, 100L, 70L, UUID.randomUUID());
        ReflectionTestUtils.setField(request, "tossPromotionKey", tossPromotionKey);
        return request;
    }
}

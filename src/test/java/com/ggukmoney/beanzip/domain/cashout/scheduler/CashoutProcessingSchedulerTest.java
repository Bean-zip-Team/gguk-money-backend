package com.ggukmoney.beanzip.domain.cashout.scheduler;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.global.client.toss.TossPromotionClient;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.cashout.repository.CashoutRequestRepository;
import com.ggukmoney.beanzip.domain.cashout.service.CashoutService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashoutProcessingSchedulerTest {

    private final CashoutRequestRepository cashoutRequestRepository = mock(CashoutRequestRepository.class);
    private final AuthIdentityRepository authIdentityRepository = mock(AuthIdentityRepository.class);
    private final TossPromotionClient tossPromotionClient = mock(TossPromotionClient.class);
    private final CashoutService cashoutService = mock(CashoutService.class);

    @Test
    void limitsOneTickAndLooksUpTossIdentitiesOnceForThePage() {
        CashoutFixture first = cashoutFixture(1L, "key-1");
        CashoutFixture second = cashoutFixture(2L, "key-2");
        CashoutProcessingScheduler scheduler = scheduler(2);
        List<AuthIdentityRepository.UserProviderIdentity> identities = List.of(
                identity(first.userId(), "toss-user-1"),
                identity(second.userId(), "toss-user-2")
        );
        when(cashoutRequestRepository.findProcessingAfterId(
                CashoutRequest.Status.PROCESSING, 0L, PageRequest.of(0, 2)))
                .thenReturn(List.of(first.request(), second.request()));
        when(authIdentityRepository.findProviderIdentitiesByUserIds(
                Set.of(first.userId(), second.userId()), AuthIdentity.Provider.TOSS))
                .thenReturn(identities);
        when(tossPromotionClient.getExecutionResult("toss-user-1", "TEST_PROMO", "key-1"))
                .thenReturn(TossPromotionClient.PromotionResultStatus.SUCCESS);
        when(tossPromotionClient.getExecutionResult("toss-user-2", "TEST_PROMO", "key-2"))
                .thenReturn(TossPromotionClient.PromotionResultStatus.PENDING);

        scheduler.pollProcessingCashouts();

        verify(authIdentityRepository).findProviderIdentitiesByUserIds(
                Set.of(first.userId(), second.userId()), AuthIdentity.Provider.TOSS);
        InOrder executionOrder = inOrder(tossPromotionClient);
        executionOrder.verify(tossPromotionClient)
                .getExecutionResult("toss-user-1", "TEST_PROMO", "key-1");
        executionOrder.verify(tossPromotionClient)
                .getExecutionResult("toss-user-2", "TEST_PROMO", "key-2");
        verify(cashoutService).finalizeProcessingCashout(
                first.request(), TossPromotionClient.PromotionResultStatus.SUCCESS);
        verify(cashoutService).finalizeProcessingCashout(
                second.request(), TossPromotionClient.PromotionResultStatus.PENDING);
    }

    @Test
    void carriesCursorAcrossTicksAndWrapsAtMostOnceAtTheEnd() {
        CashoutFixture first = cashoutFixture(1L, "key-1");
        CashoutFixture second = cashoutFixture(2L, "key-2");
        CashoutFixture third = cashoutFixture(3L, "key-3");
        CashoutProcessingScheduler scheduler = scheduler(2);
        PageRequest page = PageRequest.of(0, 2);
        when(cashoutRequestRepository.findProcessingAfterId(CashoutRequest.Status.PROCESSING, 0L, page))
                .thenReturn(List.of(first.request(), second.request()), List.of(first.request(), second.request()));
        when(cashoutRequestRepository.findProcessingAfterId(CashoutRequest.Status.PROCESSING, 2L, page))
                .thenReturn(List.of(third.request()));
        when(cashoutRequestRepository.findProcessingAfterId(CashoutRequest.Status.PROCESSING, 3L, page))
                .thenReturn(List.of());
        stubIdentities(first, second, third);
        when(tossPromotionClient.getExecutionResult(any(), eq("TEST_PROMO"), any()))
                .thenReturn(TossPromotionClient.PromotionResultStatus.PENDING);

        scheduler.pollProcessingCashouts();
        scheduler.pollProcessingCashouts();
        scheduler.pollProcessingCashouts();

        verify(cashoutRequestRepository).findProcessingAfterId(CashoutRequest.Status.PROCESSING, 2L, page);
        verify(cashoutRequestRepository).findProcessingAfterId(CashoutRequest.Status.PROCESSING, 3L, page);
        verify(cashoutRequestRepository, times(2))
                .findProcessingAfterId(CashoutRequest.Status.PROCESSING, 0L, page);
    }

    @Test
    void advancesCursorAndContinuesAfterMissingIdentityAndTossFailure() {
        CashoutFixture missingIdentity = cashoutFixture(1L, "key-1");
        CashoutFixture tossFailure = cashoutFixture(2L, "key-2");
        CashoutFixture success = cashoutFixture(3L, "key-3");
        CashoutProcessingScheduler scheduler = scheduler(3);
        PageRequest page = PageRequest.of(0, 3);
        List<AuthIdentityRepository.UserProviderIdentity> identities = List.of(
                identity(tossFailure.userId(), "toss-user-2"),
                identity(success.userId(), "toss-user-3")
        );
        when(cashoutRequestRepository.findProcessingAfterId(CashoutRequest.Status.PROCESSING, 0L, page))
                .thenReturn(List.of(missingIdentity.request(), tossFailure.request(), success.request()), List.of());
        when(cashoutRequestRepository.findProcessingAfterId(CashoutRequest.Status.PROCESSING, 3L, page))
                .thenReturn(List.of());
        when(authIdentityRepository.findProviderIdentitiesByUserIds(
                Set.of(missingIdentity.userId(), tossFailure.userId(), success.userId()),
                AuthIdentity.Provider.TOSS))
                .thenReturn(identities);
        when(tossPromotionClient.getExecutionResult("toss-user-2", "TEST_PROMO", "key-2"))
                .thenThrow(new RuntimeException("network error"));
        when(tossPromotionClient.getExecutionResult("toss-user-3", "TEST_PROMO", "key-3"))
                .thenReturn(TossPromotionClient.PromotionResultStatus.SUCCESS);

        scheduler.pollProcessingCashouts();
        scheduler.pollProcessingCashouts();

        verify(tossPromotionClient, never()).getExecutionResult(any(), eq("TEST_PROMO"), eq("key-1"));
        verify(cashoutService, never()).finalizeProcessingCashout(eq(tossFailure.request()), any());
        verify(cashoutService).finalizeProcessingCashout(
                success.request(), TossPromotionClient.PromotionResultStatus.SUCCESS);
        verify(cashoutRequestRepository).findProcessingAfterId(
                CashoutRequest.Status.PROCESSING, 3L, page);
    }

    @Test
    void rejectsBatchSizesOutsideOneToOneHundred() {
        assertThatThrownBy(() -> scheduler(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
        assertThatThrownBy(() -> scheduler(101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
    }

    private CashoutProcessingScheduler scheduler(int batchSize) {
        return new CashoutProcessingScheduler(
                cashoutRequestRepository,
                authIdentityRepository,
                tossPromotionClient,
                cashoutService,
                batchSize,
                "TEST_PROMO"
        );
    }

    private void stubIdentities(CashoutFixture first, CashoutFixture second, CashoutFixture third) {
        List<AuthIdentityRepository.UserProviderIdentity> firstPageIdentities = List.of(
                identity(first.userId(), "toss-user-1"),
                identity(second.userId(), "toss-user-2")
        );
        List<AuthIdentityRepository.UserProviderIdentity> thirdPageIdentities =
                List.of(identity(third.userId(), "toss-user-3"));
        when(authIdentityRepository.findProviderIdentitiesByUserIds(
                Set.of(first.userId(), second.userId()), AuthIdentity.Provider.TOSS))
                .thenReturn(firstPageIdentities);
        when(authIdentityRepository.findProviderIdentitiesByUserIds(
                Set.of(third.userId()), AuthIdentity.Provider.TOSS))
                .thenReturn(thirdPageIdentities);
    }

    private AuthIdentityRepository.UserProviderIdentity identity(UUID userId, String providerUserId) {
        AuthIdentityRepository.UserProviderIdentity identity = mock(AuthIdentityRepository.UserProviderIdentity.class);
        when(identity.getUserId()).thenReturn(userId);
        when(identity.getProviderUserId()).thenReturn(providerUserId);
        return identity;
    }

    private CashoutFixture cashoutFixture(long id, String tossPromotionKey) {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        CashoutRequest request = CashoutRequest.createFor(user, 100L, 70L, UUID.randomUUID());
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(request, "tossPromotionKey", tossPromotionKey);
        return new CashoutFixture(request, userId);
    }

    private record CashoutFixture(CashoutRequest request, UUID userId) {
    }
}

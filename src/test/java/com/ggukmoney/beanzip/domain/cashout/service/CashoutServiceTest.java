package com.ggukmoney.beanzip.domain.cashout.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.global.client.toss.TossPromotionClient;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutListPageResponse;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashoutServiceTest {

    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
    private final CashoutRequestRepository cashoutRequestRepository = mock(CashoutRequestRepository.class);
    private final AuthIdentityRepository authIdentityRepository = mock(AuthIdentityRepository.class);
    private final TossPromotionClient tossPromotionClient = mock(TossPromotionClient.class);
    private final UserService userService = mock(UserService.class);
    private final CashoutPolicyConfig cashoutPolicyConfig = mock(CashoutPolicyConfig.class);
    private final CashoutService cashoutService = new CashoutService(
            pointAccountService, pointLedgerService, cashoutRequestRepository,
            authIdentityRepository, tossPromotionClient, userService, cashoutPolicyConfig,
            new NoOpTransactionManager());

    private final UUID userId = UUID.randomUUID();
    private final UUID idempotencyKey = UUID.randomUUID();

    @BeforeEach
    void stubCashoutPolicyDefaults() {
        when(cashoutPolicyConfig.minimumPoint()).thenReturn(10);
        when(cashoutPolicyConfig.pointToKrwRate()).thenReturn(new BigDecimal("0.7"));
        ReflectionTestUtils.setField(cashoutService, "promotionCode", "TEST_PROMO");
    }

    private void stubTossIdentity() {
        AuthIdentity identity = AuthIdentity.toss(mock(AppUser.class), "toss-user-key-1");
        when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS))
                .thenReturn(Optional.of(identity));
    }

    @Test
    void floorsTossPointAmountForFigmaVerifiedExample() {
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        CashoutQuoteResponse response = cashoutService.getQuote(userId);

        assertThat(response.pointBalance()).isEqualTo(134L);
        assertThat(response.tossPointAmount()).isEqualTo(93L);
        assertThat(response.eligible()).isTrue();
        assertThat(response.minimumPoint()).isEqualTo(10);
        assertThat(response.rate().pointToKrw()).isEqualByComparingTo(new BigDecimal("0.7"));
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
        when(cashoutPolicyConfig.pointToKrwRate()).thenReturn(new BigDecimal("0.5"));
        when(pointAccountService.getBalance(userId)).thenReturn(100L);

        CashoutQuoteResponse response = cashoutService.getQuote(userId);

        assertThat(response.minimumPoint()).isEqualTo(50);
        assertThat(response.rate().pointToKrw()).isEqualByComparingTo(new BigDecimal("0.5"));
        assertThat(response.tossPointAmount()).isEqualTo(50L);
    }

    @Test
    void leavesTheRoundedOffRemainderInTheBalance() {
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        CashoutQuoteResponse response = cashoutService.getQuote(userId);

        // 134P 를 전부 빼고 93P 를 주던 것을, 같은 93P 를 만드는 133P 만 빼도록 바꿨다.
        assertThat(response.redeemablePoint()).isEqualTo(133L);
        assertThat(response.remainingPoint()).isEqualTo(1L);
        assertThat(response.tossPointAmount()).isEqualTo(93L);
    }

    @Test
    void debitsOnlyTheRedeemablePortion() {
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        when(pointAccountService.debit(userId, 133L)).thenReturn(mock(PointAccount.class));
        when(cashoutRequestRepository.save(any(CashoutRequest.class))).thenAnswer(i -> i.getArgument(0));
        stubTossIdentity();
        when(tossPromotionClient.getKey("toss-user-key-1")).thenReturn("promo-key-1");
        when(tossPromotionClient.executePromotion(any(), any(), any(), anyLong()))
                .thenReturn(TossPromotionClient.PromotionExecutionOutcome.success());

        cashoutService.submit(userId, idempotencyKey);

        verify(pointAccountService).debit(userId, 133L);
        verify(pointAccountService, never()).debit(userId, 134L);
    }

    @Test
    void roundingUnitFollowsTheConfiguredRate() {
        // rate 0.02 면 50P 단위. 99P 는 1P 로 환산되고 50P 만 빠진다.
        when(cashoutPolicyConfig.minimumPoint()).thenReturn(50);
        when(cashoutPolicyConfig.pointToKrwRate()).thenReturn(new BigDecimal("0.02"));
        when(pointAccountService.getBalance(userId)).thenReturn(99L);

        CashoutQuoteResponse response = cashoutService.getQuote(userId);

        assertThat(response.tossPointAmount()).isEqualTo(1L);
        assertThat(response.redeemablePoint()).isEqualTo(50L);
        assertThat(response.remainingPoint()).isEqualTo(49L);
    }

    @Test
    void leavesNothingBehindWhenBalanceIsAnExactMultiple() {
        when(cashoutPolicyConfig.minimumPoint()).thenReturn(50);
        when(cashoutPolicyConfig.pointToKrwRate()).thenReturn(new BigDecimal("0.02"));
        when(pointAccountService.getBalance(userId)).thenReturn(100L);

        CashoutQuoteResponse response = cashoutService.getQuote(userId);

        assertThat(response.redeemablePoint()).isEqualTo(100L);
        assertThat(response.remainingPoint()).isZero();
    }

    @Test
    void refundsExactlyWhatWasDebitedOnFailure() {
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        when(pointAccountService.debit(userId, 133L)).thenReturn(mock(PointAccount.class));
        when(cashoutRequestRepository.save(any(CashoutRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS))
                .thenReturn(Optional.empty());

        cashoutService.submit(userId, idempotencyKey);

        // 환불은 request.pointAmount 를 쓴다. 차감분과 같은 값이어야 잔액이 어긋나지 않는다.
        verify(pointAccountService).credit(userId, 133L);
    }

    @Test
    void submitsFullBalanceAndTransitionsToProcessingOnTossSuccess() {
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);

        PointAccount account = mock(PointAccount.class);
        when(pointAccountService.debit(userId, 133L)).thenReturn(account);

        when(cashoutRequestRepository.save(any(CashoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        stubTossIdentity();
        when(tossPromotionClient.getKey("toss-user-key-1")).thenReturn("promo-key-1");
        when(tossPromotionClient.executePromotion("toss-user-key-1", "TEST_PROMO", "promo-key-1", 93L))
                .thenReturn(TossPromotionClient.PromotionExecutionOutcome.success());

        CashoutSubmitResponse response = cashoutService.submit(userId, idempotencyKey);

        assertThat(response.pointAmount()).isEqualTo(133L);
        assertThat(response.tossPointAmount()).isEqualTo(93L);
        assertThat(response.status()).isEqualTo("PROCESSING");
        verify(pointLedgerService).recordDebit(account, user, 133L, "CASHOUT", idempotencyKey);
        verify(pointAccountService, never()).credit(any(), anyLong());
    }

    @Test
    void marksFailedAndRefundsWhenNoLinkedTossIdentity() {
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        when(pointAccountService.debit(userId, 133L)).thenReturn(mock(PointAccount.class));
        when(cashoutRequestRepository.save(any(CashoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS)).thenReturn(Optional.empty());

        CashoutSubmitResponse response = cashoutService.submit(userId, idempotencyKey);

        assertThat(response.status()).isEqualTo("FAILED");
        verify(pointAccountService).credit(userId, 133L);
        verify(pointLedgerService).recordReversal(any(), any(), eq(133L), any(), any());
    }

    @Test
    void marksFailedAndRefundsWhenGetKeyFails() {
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        when(pointAccountService.debit(userId, 133L)).thenReturn(mock(PointAccount.class));
        when(cashoutRequestRepository.save(any(CashoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        stubTossIdentity();
        when(tossPromotionClient.getKey("toss-user-key-1")).thenThrow(new RuntimeException("network error"));

        CashoutSubmitResponse response = cashoutService.submit(userId, idempotencyKey);

        assertThat(response.status()).isEqualTo("FAILED");
        verify(pointAccountService).credit(userId, 133L);
    }

    @Test
    void marksFailedAndRefundsOnExplicitTossRejection() {
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        when(pointAccountService.debit(userId, 133L)).thenReturn(mock(PointAccount.class));
        when(cashoutRequestRepository.save(any(CashoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        stubTossIdentity();
        when(tossPromotionClient.getKey("toss-user-key-1")).thenReturn("promo-key-1");
        when(tossPromotionClient.executePromotion("toss-user-key-1", "TEST_PROMO", "promo-key-1", 93L))
                .thenReturn(TossPromotionClient.PromotionExecutionOutcome.failed("4112", "예산 부족"));

        CashoutSubmitResponse response = cashoutService.submit(userId, idempotencyKey);

        assertThat(response.status()).isEqualTo("FAILED");
        verify(pointAccountService).credit(userId, 133L);
    }

    @Test
    void leavesRequestedAndDoesNotRefundOnAmbiguousTossFailure() {
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(134L);

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        when(pointAccountService.debit(userId, 133L)).thenReturn(mock(PointAccount.class));
        when(cashoutRequestRepository.save(any(CashoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        stubTossIdentity();
        when(tossPromotionClient.getKey("toss-user-key-1")).thenReturn("promo-key-1");
        when(tossPromotionClient.executePromotion("toss-user-key-1", "TEST_PROMO", "promo-key-1", 93L))
                .thenThrow(new TossPromotionClient.AmbiguousTossFailureException("timeout"));

        CashoutSubmitResponse response = cashoutService.submit(userId, idempotencyKey);

        assertThat(response.status()).isEqualTo("REQUESTED");
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(pointLedgerService, never()).recordReversal(any(), any(), anyLong(), any(), any());
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
        CashoutRequest existingRequest = CashoutRequest.createFor(user, 133L, 93L, idempotencyKey);
        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.of(existingRequest));

        CashoutSubmitResponse response = cashoutService.submit(userId, idempotencyKey);

        assertThat(response.pointAmount()).isEqualTo(133L);
        assertThat(response.tossPointAmount()).isEqualTo(93L);
        verify(pointAccountService, never()).debit(any(), anyLong());
        verify(cashoutRequestRepository, never()).save(any());
    }

    @Test
    void replaysWinningRequestWhenConcurrentSubmissionWithSameIdempotencyKeyRaces() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        CashoutRequest winningRequest = CashoutRequest.createFor(user, 133L, 93L, idempotencyKey);

        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.empty(), Optional.of(winningRequest));
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(134L);
        when(pointAccountService.debit(userId, 133L)).thenReturn(mock(PointAccount.class));
        when(cashoutRequestRepository.save(any(CashoutRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        CashoutSubmitResponse response = cashoutService.submit(userId, idempotencyKey);

        assertThat(response.pointAmount()).isEqualTo(133L);
        assertThat(response.tossPointAmount()).isEqualTo(93L);
        verify(tossPromotionClient, never()).getKey(any());
    }

    @Test
    void returnsConflictWhenConcurrentDebitLosesOptimisticLock() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);

        when(cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cashoutRequestRepository.existsByUserIdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(pointAccountService.getBalance(userId)).thenReturn(134L);
        when(pointAccountService.debit(userId, 133L))
                .thenThrow(new OptimisticLockingFailureException("version conflict"));

        assertThatThrownBy(() -> cashoutService.submit(userId, idempotencyKey))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CASHOUT_ALREADY_PROCESSING");

        verify(cashoutRequestRepository, never()).save(any());
        verify(tossPromotionClient, never()).getKey(any());
    }

    @Test
    void returnsHasMoreFalseWhenFetchedCountEqualsPageSize() {
        List<CashoutRequest> requests = List.of(
                cashoutRequestFixture(3L, CashoutRequest.Status.REQUESTED),
                cashoutRequestFixture(2L, CashoutRequest.Status.REQUESTED),
                cashoutRequestFixture(1L, CashoutRequest.Status.REQUESTED)
        );
        when(cashoutRequestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(requests));

        CashoutListPageResponse response = cashoutService.list(userId, null, 3, null);

        assertThat(response.items()).hasSize(3);
        assertThat(response.hasMore()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void returnsHasMoreTrueAndTrimsExtraRowWhenExceedingPageSize() {
        List<CashoutRequest> requests = List.of(
                cashoutRequestFixture(4L, CashoutRequest.Status.REQUESTED),
                cashoutRequestFixture(3L, CashoutRequest.Status.REQUESTED),
                cashoutRequestFixture(2L, CashoutRequest.Status.REQUESTED),
                cashoutRequestFixture(1L, CashoutRequest.Status.REQUESTED)
        );
        when(cashoutRequestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(requests));

        CashoutListPageResponse response = cashoutService.list(userId, null, 3, null);

        assertThat(response.items()).hasSize(3);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isNotNull();
    }

    @Test
    void throwsOnMalformedCursor() {
        assertThatThrownBy(() -> cashoutService.list(userId, "not-a-valid-cursor!!", 20, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMMON_VALIDATION_ERROR");
    }

    @Test
    void exposesCompletedAtOnlyForTerminalStatus() {
        CashoutRequest requested = cashoutRequestFixture(2L, CashoutRequest.Status.REQUESTED);
        CashoutRequest succeeded = cashoutRequestFixture(1L, CashoutRequest.Status.SUCCEEDED);
        when(cashoutRequestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(List.of(requested, succeeded)));

        CashoutListPageResponse response = cashoutService.list(userId, null, 20, null);

        assertThat(response.items().get(0).completedAt()).isNull();
        assertThat(response.items().get(1).completedAt()).isNotNull();
    }

    @Test
    void returnsDetailForOwnedCashoutRequest() {
        UUID cashoutId = UUID.randomUUID();
        CashoutRequest request = cashoutRequestFixture(1L, CashoutRequest.Status.REQUESTED);
        ReflectionTestUtils.setField(request, "publicId", cashoutId);
        when(cashoutRequestRepository.findByPublicIdAndUserId(cashoutId, userId)).thenReturn(Optional.of(request));

        var response = cashoutService.getDetail(userId, cashoutId);

        assertThat(response.cashoutId()).isEqualTo(cashoutId);
        assertThat(response.pointAmount()).isEqualTo(100L);
        assertThat(response.tossPointAmount()).isEqualTo(70L);
        assertThat(response.status()).isEqualTo("REQUESTED");
    }

    @Test
    void throwsNotFoundWhenCashoutRequestDoesNotExist() {
        UUID cashoutId = UUID.randomUUID();
        when(cashoutRequestRepository.findByPublicIdAndUserId(cashoutId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashoutService.getDetail(userId, cashoutId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CASHOUT_NOT_FOUND");
    }

    @Test
    void throwsNotFoundWhenCashoutRequestBelongsToAnotherUser() {
        UUID cashoutId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(cashoutRequestRepository.findByPublicIdAndUserId(cashoutId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashoutService.getDetail(otherUserId, cashoutId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CASHOUT_NOT_FOUND");
    }

    private Page<CashoutRequest> pageOf(List<CashoutRequest> requests) {
        return new PageImpl<>(requests);
    }

    private CashoutRequest cashoutRequestFixture(long id, CashoutRequest.Status status) {
        AppUser user = mock(AppUser.class);
        CashoutRequest request = CashoutRequest.createFor(user, 100L, 70L, UUID.randomUUID());
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(request, "status", status);
        ReflectionTestUtils.setField(request, "createdAt", Instant.now());
        ReflectionTestUtils.setField(request, "updatedAt", Instant.now());
        return request;
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}

package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.tap.config.TapPolicyConfig;
import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.infra.TapRateLimiter;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TapComplexServiceTest {

    private final TapBatchService tapBatchService = mock(TapBatchService.class);
    private final UserTapDailyService userTapDailyService = mock(UserTapDailyService.class);
    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
    private final TapRateLimiter tapRateLimiter = mock(TapRateLimiter.class);
    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final TapCurveCalculator tapCurveCalculator = mock(TapCurveCalculator.class);
    private final TapValidityCalculator tapValidityCalculator = mock(TapValidityCalculator.class);
    private final TapBotDetector tapBotDetector = mock(TapBotDetector.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);

    private final TapComplexService tapComplexService = new TapComplexService(
            tapBatchService, userTapDailyService, pointAccountService, pointLedgerService,
            tapRateLimiter, tapPolicyConfig, tapCurveCalculator, tapValidityCalculator,
            tapBotDetector, appUserRepository
    );

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void allowRateLimitByDefault() {
        when(tapRateLimiter.tryConsume(eq(userId), anyInt(), any(Double.class))).thenReturn(true);
    }

    @Test
    void throwsRateLimitedWhenBucketExhausted() {
        when(tapRateLimiter.tryConsume(eq(userId), anyInt(), any(Double.class))).thenReturn(false);

        TapSubmitCommand command = new TapSubmitCommand(userId, sessionId, 1L, 50);

        assertThatThrownBy(() -> tapComplexService.submitBatch(command))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TAP_RATE_LIMITED");
        verify(appUserRepository, never()).findById(any());
    }

    @Test
    void returnsExistingSnapshotWithoutReprocessingOnDuplicateSubmission() {
        TapBatch existing = mock(TapBatch.class);
        when(existing.getAcceptedCount()).thenReturn(42);
        when(tapBatchService.findExisting(userId, sessionId, 1L)).thenReturn(Optional.of(existing));
        when(pointAccountService.getBalance(userId)).thenReturn(100L);

        TapSubmitCommand command = new TapSubmitCommand(userId, sessionId, 1L, 50);
        TapSubmitOutcome outcome = tapComplexService.submitBatch(command);

        assertThat(outcome.acceptedCount()).isEqualTo(42);
        assertThat(outcome.pointsAwarded()).isZero();
        assertThat(outcome.balance()).isEqualTo(100L);
        verify(appUserRepository, never()).findById(any());
        verify(userTapDailyService, never()).getOrCreateToday(any(), any(), any());
        verify(pointAccountService, never()).credit(any(), anyLong());
    }

    @Test
    void doesNotAccumulateValidTapsWhenBotSuspected() {
        AppUser user = stubUser();
        when(tapBatchService.findExisting(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapBatchService.findRecentForBotCheck(eq(userId), anyInt())).thenReturn(List.of());
        when(tapBotDetector.isSuspicious(any(), eq(tapPolicyConfig))).thenReturn(true);
        stubCommonPolicy();

        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.now(), 50);
        when(userTapDailyService.getOrCreateToday(eq(user), eq(tapCurveCalculator), eq(tapPolicyConfig))).thenReturn(daily);
        when(tapValidityCalculator.calculateAcceptedCount(eq(100), any(), anyInt(), anyInt(), eq(tapPolicyConfig)))
                .thenReturn(100);
        when(tapBatchService.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        TapSubmitCommand command = new TapSubmitCommand(userId, sessionId, 1L, 100);
        TapSubmitOutcome outcome = tapComplexService.submitBatch(command);

        assertThat(outcome.acceptedCount()).isEqualTo(100);
        assertThat(outcome.pointsAwarded()).isZero();
        assertThat(daily.getValidTapCount()).isZero();
        verify(userTapDailyService, never()).save(any());
        verify(pointAccountService, never()).credit(any(), anyLong());

        ArgumentCaptor<TapBatch> batchCaptor = ArgumentCaptor.forClass(TapBatch.class);
        verify(tapBatchService).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().isBotSuspected()).isTrue();
    }

    @Test
    void awardsPointAndRedrawsTargetWhenValidTapsReachTarget() {
        AppUser user = stubUser();
        when(tapBatchService.findExisting(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapBatchService.findRecentForBotCheck(eq(userId), anyInt())).thenReturn(List.of());
        when(tapBotDetector.isSuspicious(any(), eq(tapPolicyConfig))).thenReturn(false);
        stubCommonPolicy();
        when(tapPolicyConfig.pointDailyCap()).thenReturn(20);

        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.now(), 100);
        when(userTapDailyService.getOrCreateToday(eq(user), eq(tapCurveCalculator), eq(tapPolicyConfig))).thenReturn(daily);
        when(tapValidityCalculator.calculateAcceptedCount(eq(100), any(), anyInt(), anyInt(), eq(tapPolicyConfig)))
                .thenReturn(100);

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchService.save(any(TapBatch.class))).thenReturn(savedBatch);

        when(tapCurveCalculator.drawNextTarget(eq(100), eq(1), eq(tapPolicyConfig))).thenReturn(400);

        PointAccount account = PointAccount.createFor(user);
        account.credit(1);
        when(pointAccountService.credit(userId, 1)).thenReturn(account);

        TapSubmitCommand command = new TapSubmitCommand(userId, sessionId, 1L, 100);
        TapSubmitOutcome outcome = tapComplexService.submitBatch(command);

        assertThat(outcome.acceptedCount()).isEqualTo(100);
        assertThat(outcome.pointsAwarded()).isEqualTo(1);
        assertThat(outcome.balance()).isEqualTo(1L);
        assertThat(daily.getNextPointTarget()).isEqualTo(400);
        assertThat(daily.getPointEarnedAmount()).isEqualTo(1);
        verify(pointLedgerService).recordCredit(eq(account), eq(user), eq(1L), eq("TAP_REWARD"), any(UUID.class));
        verify(userTapDailyService).save(daily);
        verify(tapRateLimiter).addMinuteCount(userId, 100);
    }

    @Test
    void stopsAwardingWhenDailyPointCapAlreadyReachedEvenIfTargetIsReached() {
        AppUser user = stubUser();
        when(tapBatchService.findExisting(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapBatchService.findRecentForBotCheck(eq(userId), anyInt())).thenReturn(List.of());
        when(tapBotDetector.isSuspicious(any(), eq(tapPolicyConfig))).thenReturn(false);
        stubCommonPolicy();
        when(tapPolicyConfig.pointDailyCap()).thenReturn(1);

        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.now(), 50);
        daily.awardPoint(60); // pointEarnedAmount already at the cap (1)
        when(userTapDailyService.getOrCreateToday(eq(user), eq(tapCurveCalculator), eq(tapPolicyConfig))).thenReturn(daily);
        when(tapValidityCalculator.calculateAcceptedCount(eq(100), any(), anyInt(), anyInt(), eq(tapPolicyConfig)))
                .thenReturn(100);

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchService.save(any(TapBatch.class))).thenReturn(savedBatch);
        when(pointAccountService.getBalance(userId)).thenReturn(7L);

        TapSubmitCommand command = new TapSubmitCommand(userId, sessionId, 1L, 100);
        TapSubmitOutcome outcome = tapComplexService.submitBatch(command);

        assertThat(daily.getValidTapCount()).isEqualTo(100);
        assertThat(outcome.pointsAwarded()).isZero();
        assertThat(outcome.balance()).isEqualTo(7L);
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(userTapDailyService).save(daily);
    }

    private void stubCommonPolicy() {
        when(tapPolicyConfig.maxPerMinute()).thenReturn(420);
        when(tapPolicyConfig.maxPerDay()).thenReturn(12000);
        when(tapRateLimiter.getMinuteCount(userId)).thenReturn(0);
    }

    private AppUser stubUser() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        return user;
    }
}

package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.global.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TapBatchServiceTest {

    private static final double JITTER_TOLERANT_STDDEV_THRESHOLD_MS = 5000.0;

    private final TapBatchRepository tapBatchRepository = mock(TapBatchRepository.class);
    private final UserTapDailyService userTapDailyService = mock(UserTapDailyService.class);
    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
    private final RedisService redisService = mock(RedisService.class);
    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final UserService userService = mock(UserService.class);

    private final TapBatchService tapBatchService = new TapBatchService(
            tapBatchRepository, userTapDailyService, pointAccountService, pointLedgerService,
            redisService, tapPolicyConfig, userService
    );

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void allowRateLimitByDefault() {
        lenient().when(redisService.executeScript(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        lenient().when(redisService.get(anyString())).thenReturn(Optional.empty());
        when(tapPolicyConfig.minIntervalMs()).thenReturn(80);
        when(tapPolicyConfig.botSampleSize()).thenReturn(10);
        when(tapPolicyConfig.botStddevThresholdMs()).thenReturn(12.0);
    }

    @Test
    void throwsRateLimitedWhenBucketExhausted() {
        when(redisService.executeScript(any(RedisScript.class), eq(List.of("tap:bucket:" + userId)), anyString(), anyString(), anyString()))
                .thenReturn(0L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 50);

        assertThatThrownBy(() -> tapBatchService.submitBatch(userId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TAP_RATE_LIMITED");
        verify(userService, never()).getById(any());
    }

    @Test
    void returnsExistingSnapshotWithoutReprocessingOnDuplicateSubmission() {
        TapBatch existing = mock(TapBatch.class);
        when(existing.getAcceptedCount()).thenReturn(42);
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.of(existing));
        when(pointAccountService.getBalance(userId)).thenReturn(100L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 50);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(42);
        assertThat(response.pointsAwarded()).isZero();
        assertThat(response.balance()).isEqualTo(100L);
        verify(userService, never()).getById(any());
        verify(userTapDailyService, never()).getOrCreateToday(any(), any());
        verify(pointAccountService, never()).credit(any(), anyLong());
    }

    @Test
    void doesNotAccumulateValidTapsWhenBatchArrivalIntervalsAreSuspiciouslyRegular() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        Instant now = Instant.now();
        TapBatch recent1 = batchWithCreatedAt(now.minusMillis(8000));
        TapBatch recent2 = batchWithCreatedAt(now.minusMillis(16000));
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(List.of(recent1, recent2));
        stubCommonPolicy();
        when(tapPolicyConfig.botStddevThresholdMs()).thenReturn(JITTER_TOLERANT_STDDEV_THRESHOLD_MS);

        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.now(), 50);
        when(userTapDailyService.getOrCreateToday(eq(user), eq(tapPolicyConfig))).thenReturn(daily);
        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.pointsAwarded()).isZero();
        assertThat(daily.getValidTapCount()).isZero();
        verify(userTapDailyService, never()).save(any());
        verify(pointAccountService, never()).credit(any(), anyLong());

        ArgumentCaptor<TapBatch> batchCaptor = ArgumentCaptor.forClass(TapBatch.class);
        verify(tapBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().isBotSuspected()).isTrue();
    }

    @Test
    void capsAcceptedCountByElapsedTimeSinceLastBatch() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        TapBatch recent = batchWithCreatedAt(Instant.now().minusMillis(4000));
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(List.of(recent));
        stubCommonPolicy();

        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.now(), 100_000);
        when(userTapDailyService.getOrCreateToday(eq(user), eq(tapPolicyConfig))).thenReturn(daily);
        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 500);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(50);
    }

    @Test
    void capsAcceptedCountByDailyRemaining() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class))).thenReturn(List.of());
        stubCommonPolicy();
        when(tapPolicyConfig.maxPerDay()).thenReturn(12000);

        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.now(), 100_000);
        daily.addValidTaps(11_995);
        when(userTapDailyService.getOrCreateToday(eq(user), eq(tapPolicyConfig))).thenReturn(daily);
        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(5);
    }

    @Test
    void awardsPointAndRedrawsTargetWhenValidTapsReachTarget() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class))).thenReturn(List.of());
        stubCommonPolicy();
        when(tapPolicyConfig.pointDailyCap()).thenReturn(20);

        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.now(), 100);
        when(userTapDailyService.getOrCreateToday(eq(user), eq(tapPolicyConfig))).thenReturn(daily);

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchRepository.save(any(TapBatch.class))).thenReturn(savedBatch);

        when(userTapDailyService.drawNextTarget(eq(100), eq(1), eq(tapPolicyConfig))).thenReturn(400);

        PointAccount account = PointAccount.createFor(user);
        account.credit(1);
        when(pointAccountService.credit(userId, 1)).thenReturn(account);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(100);
        assertThat(response.pointsAwarded()).isEqualTo(1);
        assertThat(response.balance()).isEqualTo(1L);
        assertThat(daily.getNextPointTarget()).isEqualTo(400);
        assertThat(daily.getPointEarnedAmount()).isEqualTo(1);
        verify(pointLedgerService).recordCredit(eq(account), eq(user), eq(1L), eq("TAP_REWARD"), any(UUID.class));
        verify(userTapDailyService).save(daily);
        verify(redisService).executeScript(any(RedisScript.class), eq(List.of("tap:minute:" + userId)), eq("100"), eq("60"));
    }

    @Test
    void stopsAwardingWhenDailyPointCapAlreadyReachedEvenIfTargetIsReached() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class))).thenReturn(List.of());
        stubCommonPolicy();
        when(tapPolicyConfig.pointDailyCap()).thenReturn(1);

        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.now(), 50);
        daily.awardPoint(60);
        when(userTapDailyService.getOrCreateToday(eq(user), eq(tapPolicyConfig))).thenReturn(daily);

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchRepository.save(any(TapBatch.class))).thenReturn(savedBatch);
        when(pointAccountService.getBalance(userId)).thenReturn(7L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(daily.getValidTapCount()).isEqualTo(100);
        assertThat(response.pointsAwarded()).isZero();
        assertThat(response.balance()).isEqualTo(7L);
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(userTapDailyService).save(daily);
    }

    private void stubCommonPolicy() {
        when(tapPolicyConfig.maxPerMinute()).thenReturn(420);
        when(tapPolicyConfig.maxPerDay()).thenReturn(12000);
    }

    private AppUser stubUser() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        return user;
    }

    private TapBatch batchWithCreatedAt(Instant createdAt) {
        TapBatch batch = mock(TapBatch.class);
        when(batch.getCreatedAt()).thenReturn(createdAt);
        return batch;
    }
}

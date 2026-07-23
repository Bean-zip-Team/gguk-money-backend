package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.booster.service.BoosterGrantService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreSyncRequestedEvent;
import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.global.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
    private static final int FAR_AWAY_TARGET = 10_000_000;

    private final TapBatchRepository tapBatchRepository = mock(TapBatchRepository.class);
    private final UserTapDailyService userTapDailyService = mock(UserTapDailyService.class);
    private final UserTapProgressService userTapProgressService = mock(UserTapProgressService.class);
    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
    private final KeycapBoxAccountService keycapBoxAccountService = mock(KeycapBoxAccountService.class);
    private final BoosterGrantService boosterGrantService = mock(BoosterGrantService.class);
    private final RedisService redisService = mock(RedisService.class);
    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final UserService userService = mock(UserService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Instant acceptedAt = Instant.parse("2026-07-20T15:00:00Z");
    private final ZoneId businessZoneId = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(acceptedAt, ZoneOffset.UTC);
    private final LocalDate tapDate = LocalDate.of(2026, 7, 21);

    private final TapBatchService tapBatchService = new TapBatchService(
            tapBatchRepository, userTapDailyService, userTapProgressService, pointAccountService, pointLedgerService,
            keycapBoxAccountService, boosterGrantService, redisService, tapPolicyConfig, userService,
            eventPublisher, clock, businessZoneId
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
        lenient().when(boosterGrantService.findActiveMultiplier(any(), any())).thenReturn(BigDecimal.ONE);
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
        assertThat(response.boxesDropped()).isZero();
        assertThat(response.balance()).isEqualTo(100L);
        verify(userService, never()).getById(any());
        verify(userTapDailyService, never()).getOrCreate(any(), any());
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void doesNotAccumulateValidTapsWhenBatchArrivalIntervalsAreSuspiciouslyRegular() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        TapBatch recent1 = batchWithCreatedAt(acceptedAt.minusMillis(8000));
        TapBatch recent2 = batchWithCreatedAt(acceptedAt.minusMillis(16000));
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(List.of(recent1, recent2));
        stubCommonPolicy();
        when(tapPolicyConfig.botStddevThresholdMs()).thenReturn(JITTER_TOLERANT_STDDEV_THRESHOLD_MS);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);
        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.pointsAwarded()).isZero();
        assertThat(response.boxesDropped()).isZero();
        assertThat(daily.getValidTapCount()).isZero();
        verify(userTapDailyService, never()).save(any());
        verify(userTapProgressService, never()).getForUser(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(pointAccountService, never()).credit(any(), anyLong());

        ArgumentCaptor<TapBatch> batchCaptor = ArgumentCaptor.forClass(TapBatch.class);
        verify(tapBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().isBotSuspected()).isTrue();
    }

    @Test
    void doesNotPublishRankingSyncEventWhenAcceptedCountIsZero() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class))).thenReturn(List.of());
        stubCommonPolicy();
        when(tapPolicyConfig.maxPerMinute()).thenReturn(0);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);
        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isZero();
        verify(userTapProgressService, never()).getForUser(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void capsAcceptedCountByElapsedTimeSinceLastBatch() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        TapBatch recent = batchWithCreatedAt(acceptedAt.minusMillis(4000));
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(List.of(recent));
        stubCommonPolicy();

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);
        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);
        when(userTapProgressService.getForUser(userId)).thenReturn(farAwayProgress(user));

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

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        daily.addValidTaps(11_995);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);
        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);
        when(userTapProgressService.getForUser(userId)).thenReturn(farAwayProgress(user));

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

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 100, FAR_AWAY_TARGET);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchRepository.save(any(TapBatch.class))).thenReturn(savedBatch);

        when(userTapProgressService.drawNextTarget(eq(100L), eq(1), eq(tapPolicyConfig))).thenReturn(400);

        PointAccount account = PointAccount.createFor(user);
        account.credit(1);
        when(pointAccountService.credit(userId, 1)).thenReturn(account);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(100);
        assertThat(response.pointsAwarded()).isEqualTo(1);
        assertThat(response.boxesDropped()).isZero();
        assertThat(response.balance()).isEqualTo(1L);
        assertThat(progress.getNextPointTarget()).isEqualTo(400);
        assertThat(daily.getPointEarnedAmount()).isEqualTo(1);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        InOrder inOrder = org.mockito.Mockito.inOrder(userTapDailyService, userTapProgressService, eventPublisher);
        inOrder.verify(userTapDailyService).save(daily);
        inOrder.verify(userTapProgressService).save(progress);
        inOrder.verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new RankingScoreSyncRequestedEvent(userId));
        verify(pointLedgerService).recordCredit(eq(account), eq(user), eq(1L), eq("TAP_REWARD"), any(UUID.class));
        verify(redisService).executeScript(any(RedisScript.class), eq(List.of("tap:minute:" + userId)), eq("100"), eq("60"));
    }

    @Test
    void appliesActiveBoosterMultiplierToPointCredit() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class))).thenReturn(List.of());
        stubCommonPolicy();
        when(tapPolicyConfig.pointDailyCap()).thenReturn(20);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 100, FAR_AWAY_TARGET);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchRepository.save(any(TapBatch.class))).thenReturn(savedBatch);

        when(boosterGrantService.findActiveMultiplier(eq(userId), any(Instant.class))).thenReturn(new BigDecimal("2.0"));
        when(userTapProgressService.drawNextTarget(eq(100L), eq(1), eq(tapPolicyConfig))).thenReturn(400);

        PointAccount account = PointAccount.createFor(user);
        account.credit(2);
        when(pointAccountService.credit(userId, 2L)).thenReturn(account);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.pointsAwarded()).isEqualTo(2);
        assertThat(response.balance()).isEqualTo(2L);
        verify(pointAccountService).credit(userId, 2L);
        verify(pointLedgerService).recordCredit(eq(account), eq(user), eq(2L), eq("TAP_REWARD"), any(UUID.class));
    }

    @Test
    void dropsBoxAndRedrawsBoxTargetWhenCumulativeTapsReachTarget() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class))).thenReturn(List.of());
        stubCommonPolicy();
        when(tapPolicyConfig.pointDailyCap()).thenReturn(20);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, FAR_AWAY_TARGET, 200);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchRepository.save(any(TapBatch.class))).thenReturn(savedBatch);
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        when(userTapProgressService.drawNextBoxTarget(eq(200L), eq(tapPolicyConfig))).thenReturn(450);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 200);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(200);
        assertThat(response.pointsAwarded()).isZero();
        assertThat(response.boxesDropped()).isEqualTo(1);
        assertThat(progress.getNextBoxTarget()).isEqualTo(450);
        verify(keycapBoxAccountService).addBoxes(userId, 1);
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(userTapProgressService).save(progress);
    }

    @Test
    void stopsAwardingWhenDailyPointCapAlreadyReachedEvenIfTargetIsReached() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class))).thenReturn(List.of());
        stubCommonPolicy();
        when(tapPolicyConfig.pointDailyCap()).thenReturn(1);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        daily.incrementPointEarned();
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 50, FAR_AWAY_TARGET);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchRepository.save(any(TapBatch.class))).thenReturn(savedBatch);
        when(pointAccountService.getBalance(userId)).thenReturn(7L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(daily.getValidTapCount()).isEqualTo(100);
        assertThat(response.pointsAwarded()).isZero();
        assertThat(response.boxesDropped()).isZero();
        assertThat(response.balance()).isEqualTo(7L);
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(userTapDailyService).save(daily);
        verify(userTapProgressService).save(progress);
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

    private UserTapProgress farAwayProgress(AppUser user) {
        return UserTapProgress.createFor(user, FAR_AWAY_TARGET, FAR_AWAY_TARGET);
    }
}

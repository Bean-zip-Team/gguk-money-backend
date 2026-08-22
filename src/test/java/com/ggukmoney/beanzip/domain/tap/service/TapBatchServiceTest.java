package com.ggukmoney.beanzip.domain.tap.service;

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
import com.ggukmoney.beanzip.domain.tap.entity.UserTapSession;
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
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ResponseStatusException;

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

    private static final int FAR_AWAY_TARGET = 10_000_000;

    private final TapBatchRepository tapBatchRepository = mock(TapBatchRepository.class);
    private final UserTapDailyService userTapDailyService = mock(UserTapDailyService.class);
    private final UserTapProgressService userTapProgressService = mock(UserTapProgressService.class);
    private final UserTapSessionService userTapSessionService = mock(UserTapSessionService.class);
    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
    private final KeycapBoxAccountService keycapBoxAccountService = mock(KeycapBoxAccountService.class);
    private final RedisService redisService = mock(RedisService.class);
    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final UserService userService = mock(UserService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Instant acceptedAt = Instant.parse("2026-07-20T15:00:00Z");
    private final ZoneId businessZoneId = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(acceptedAt, ZoneOffset.UTC);
    private final LocalDate tapDate = LocalDate.of(2026, 7, 21);

    private final TapBatchService tapBatchService = new TapBatchService(
            tapBatchRepository, userTapDailyService, userTapProgressService, userTapSessionService,
            pointAccountService, pointLedgerService,
            keycapBoxAccountService, redisService, tapPolicyConfig, userService,
            eventPublisher, clock, businessZoneId
    );

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void allowRateLimitByDefault() {
        lenient().when(redisService.executeScript(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        lenient().when(tapPolicyConfig.rateLimitEnabled()).thenReturn(true);
        lenient().when(tapPolicyConfig.maxPerDay()).thenReturn(FAR_AWAY_TARGET);
        lenient().when(userTapSessionService.getOrCreateActiveSession(any(), any(), any()))
                .thenReturn(farAwaySession());
    }

    @Test
    void throwsRateLimitedWhenBucketExhausted() {
        when(redisService.executeScript(any(RedisScript.class), eq(List.of("ggukmoney:tap:bucket:" + userId)), anyString(), anyString(), anyString()))
                .thenReturn(0L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 50);

        assertThatThrownBy(() -> tapBatchService.submitBatch(userId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TAP_RATE_LIMITED");
        verify(userService, never()).getById(any());
    }

    @Test
    void returnsExistingSnapshotWithoutReprocessingOnDuplicateSubmission() {
        AppUser user = stubUser();
        TapBatch existing = mock(TapBatch.class);
        when(existing.getAcceptedCount()).thenReturn(42);
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.of(existing));
        when(pointAccountService.getBalance(userId)).thenReturn(100L);
        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        daily.addValidTaps(77);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 50);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(42);
        assertThat(response.validTapCount()).isEqualTo(77);
        assertThat(response.pointsAwarded()).isZero();
        assertThat(response.boxesDropped()).isZero();
        assertThat(response.balance()).isEqualTo(100L);
        assertThat(response.boxProgressTapCount()).isZero();
        assertThat(response.nextBoxRequiredTapCount()).isEqualTo(FAR_AWAY_TARGET);
        verify(userTapDailyService, never()).save(any());
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void acceptedCountPassesThroughSubmittedCountDirectlyWithoutAnyCap() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);
        when(userTapProgressService.getForUser(userId)).thenReturn(farAwayProgress(user));
        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 500);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(500);
        assertThat(daily.getValidTapCount()).isEqualTo(500);
    }

    @Test
    void awardsPointAndRedrawsTargetWhenValidTapsReachTarget() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapPolicyConfig.pointDailyCap()).thenReturn(20);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 100);
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
        assertThat(response.pointDailyCapReached()).isFalse();
        assertThat(progress.getNextPointTarget()).isEqualTo(400);
        assertThat(daily.getPointEarnedAmount()).isEqualTo(1);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        InOrder inOrder = org.mockito.Mockito.inOrder(userTapDailyService, userTapProgressService, eventPublisher);
        inOrder.verify(userTapProgressService).save(progress);
        inOrder.verify(userTapDailyService).save(daily);
        inOrder.verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new RankingScoreSyncRequestedEvent(userId, acceptedAt));
        verify(pointLedgerService).recordCredit(eq(account), eq(user), eq(1L), eq("TAP_REWARD"), any(UUID.class));
    }

    @Test
    void dropsBoxAndRedrawsBoxTargetWhenSessionTapsReachTarget() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapPolicyConfig.pointDailyCap()).thenReturn(20);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);
        when(userTapProgressService.getForUser(userId)).thenReturn(farAwayProgress(user));

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchRepository.save(any(TapBatch.class))).thenReturn(savedBatch);
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        UserTapSession session = UserTapSession.createFor(user, acceptedAt, acceptedAt.plusSeconds(3600), 200);
        when(userTapSessionService.getOrCreateActiveSession(eq(user), eq(acceptedAt), eq(tapPolicyConfig))).thenReturn(session);
        when(userTapSessionService.drawNextBoxTargetInSession(eq(200L), eq(0), eq(tapPolicyConfig))).thenReturn(450);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 200);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(200);
        assertThat(response.pointsAwarded()).isZero();
        assertThat(response.boxesDropped()).isEqualTo(1);
        assertThat(response.boxProgressTapCount()).isEqualTo(200);
        assertThat(response.nextBoxRequiredTapCount()).isEqualTo(450);
        assertThat(session.getSessionValidTapCount()).isEqualTo(200);
        assertThat(session.getBoxesDroppedInSession()).isEqualTo(1);
        assertThat(session.getNextBoxTarget()).isEqualTo(450);
        verify(keycapBoxAccountService).addBoxes(userId, 1);
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(userTapSessionService).save(session);
    }

    @Test
    void stopsAwardingWhenDailyPointCapAlreadyReachedEvenIfTargetIsReached() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapPolicyConfig.pointDailyCap()).thenReturn(1);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        daily.incrementPointEarned();
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 50);
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
        assertThat(response.pointDailyCapReached()).isTrue();
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(userTapDailyService).save(daily);
        verify(userTapProgressService).save(progress);
    }

    @Test
    void capsProgressReflectionAtDailyTapLimitButKeepsAuditAcceptedCountUncapped() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapPolicyConfig.maxPerDay()).thenReturn(3000);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        daily.addValidTaps(2950);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);
        when(userTapProgressService.getForUser(userId)).thenReturn(farAwayProgress(user));

        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 200);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(200);
        assertThat(daily.getValidTapCount()).isEqualTo(3000);
        assertThat(daily.getTotalValidTapCount()).isEqualTo(200);

        ArgumentCaptor<TapBatch> batchCaptor = ArgumentCaptor.forClass(TapBatch.class);
        verify(tapBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getAcceptedCount()).isEqualTo(200);
    }

    @Test
    void keepsAccumulatingTotalValidTapsAndRankingSyncEvenAfterDailyCapReached() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapPolicyConfig.maxPerDay()).thenReturn(3000);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        daily.addValidTaps(3000);
        daily.addTotalValidTaps(3000);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);

        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 200);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(200);
        assertThat(response.pointsAwarded()).isZero();
        assertThat(daily.getValidTapCount()).isEqualTo(3000);
        assertThat(daily.getTotalValidTapCount()).isEqualTo(3200);
        verify(userTapDailyService).save(daily);
        verify(userTapProgressService, never()).getForUser(any());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new RankingScoreSyncRequestedEvent(userId, acceptedAt));
    }

    @Test
    void keepsDroppingBoxesAfterDailyPointTapCapReached() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapPolicyConfig.maxPerDay()).thenReturn(3000);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        daily.addValidTaps(3000);
        daily.addTotalValidTaps(3000);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);

        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);

        UserTapSession session = UserTapSession.createFor(user, acceptedAt, acceptedAt.plusSeconds(3600), 200);
        when(userTapSessionService.getOrCreateActiveSession(eq(user), eq(acceptedAt), eq(tapPolicyConfig))).thenReturn(session);
        when(userTapSessionService.drawNextBoxTargetInSession(eq(200L), eq(0), eq(tapPolicyConfig))).thenReturn(450);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 200);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        // 포인트는 일일 상한에 막히지만 상자 진행도는 인정 탭 전체로 계속 누적된다.
        assertThat(response.pointsAwarded()).isZero();
        assertThat(response.boxesDropped()).isEqualTo(1);
        assertThat(response.boxProgressTapCount()).isEqualTo(200);
        assertThat(response.nextBoxRequiredTapCount()).isEqualTo(450);
        assertThat(session.getSessionValidTapCount()).isEqualTo(200);
        verify(keycapBoxAccountService).addBoxes(userId, 1);
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(userTapSessionService).save(session);
    }

    private AppUser stubUser() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        return user;
    }

    private UserTapProgress farAwayProgress(AppUser user) {
        return UserTapProgress.createFor(user, FAR_AWAY_TARGET);
    }

    private UserTapSession farAwaySession() {
        AppUser sessionUser = mock(AppUser.class);
        return UserTapSession.createFor(sessionUser, acceptedAt, acceptedAt.plusSeconds(3600), FAR_AWAY_TARGET);
    }
}

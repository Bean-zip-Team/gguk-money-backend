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
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionGrantIssuer;
import com.ggukmoney.beanzip.domain.promotion.service.TapThousandCompletionTrigger;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
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
import java.time.Duration;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig = mock(KeycapBoxPolicyConfig.class);
    private final PromotionPolicyConfig promotionPolicyConfig = mock(PromotionPolicyConfig.class);
    private final PromotionGrantIssuer promotionGrantIssuer = mock(PromotionGrantIssuer.class);
    private final TapThousandCompletionTrigger tapThousandCompletionTrigger = mock(TapThousandCompletionTrigger.class);
    private final UserService userService = mock(UserService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Instant acceptedAt = Instant.parse("2026-07-20T15:00:00Z");
    private final ZoneId businessZoneId = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(acceptedAt, ZoneOffset.UTC);
    private final LocalDate tapDate = LocalDate.of(2026, 7, 21);

    private final TapBatchService tapBatchService = new TapBatchService(
            tapBatchRepository, userTapDailyService, userTapProgressService, userTapSessionService,
            pointAccountService, pointLedgerService,
            keycapBoxAccountService, redisService, tapPolicyConfig, keycapBoxPolicyConfig,
            promotionPolicyConfig, promotionGrantIssuer, tapThousandCompletionTrigger, userService,
            eventPublisher, clock, businessZoneId
    );

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final AppUser accountOwner = mock(AppUser.class);
    private final PointAccount pointAccount = PointAccount.createFor(accountOwner);
    private final KeycapBoxAccount boxAccount = KeycapBoxAccount.createFor(accountOwner, acceptedAt);

    @BeforeEach
    void allowRateLimitByDefault() {
        lenient().when(redisService.executeScript(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        lenient().when(tapPolicyConfig.rateLimitEnabled()).thenReturn(true);
        lenient().when(tapPolicyConfig.maxPerDay()).thenReturn(FAR_AWAY_TARGET);
        lenient().when(userTapSessionService.getOrCreateActiveSession(any(), any(), any()))
                .thenReturn(farAwaySession());
        // 배치 응답이 항상 담는 상태라 어떤 경로에서도 한 번씩 조회된다.
        lenient().when(pointAccountService.getForUser(userId)).thenReturn(pointAccount);
        lenient().when(keycapBoxAccountService.getForUser(userId)).thenReturn(boxAccount);
        lenient().when(userTapProgressService.getForUser(userId)).thenReturn(farAwayProgress(accountOwner));
        lenient().when(keycapBoxPolicyConfig.openCycleDuration()).thenReturn(Duration.ofSeconds(60));
        lenient().when(keycapBoxPolicyConfig.freeOpenLimit()).thenReturn(2);
        lenient().when(keycapBoxPolicyConfig.adOpenLimit()).thenReturn(2);
        // 남은 탭 계산은 상한 상태까지 반영하는 실제 구현을 그대로 쓴다.
        lenient().when(userTapProgressService.remainingTapsToNextPoint(any(), any(), any())).thenCallRealMethod();
        lenient().when(tapPolicyConfig.pointDailyCap()).thenReturn(150);
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
        pointAccount.credit(100L);
        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        // 실제 적립 경로는 두 카운트를 항상 함께 올린다.
        daily.addValidTaps(77);
        daily.addTotalValidTaps(77);
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

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 500);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(500);
        assertThat(daily.getValidTapCount()).isEqualTo(500);
    }

    @Test
    void doesNotIssueTapPromotionWhileTheMissionSwitchIsOff() {
        UserTapProgress progress = prepareTapPromotionFixture(999L);
        when(tapThousandCompletionTrigger.issuingEnabled()).thenReturn(false);

        tapBatchService.submitBatch(userId, new TapBatchSubmitRequest(sessionId, 1L, 100));

        verify(promotionGrantIssuer, never()).issueIfEligible(any(), any());
        // 스위치가 꺼져 있으면 기준값도 잡지 않는다. 미리 잡으면 켜기 전에 임계를 넘긴 유저가
        // 통과 순간을 놓쳐 영영 못 받는다.
        assertThat(progress.hasPromotionTapBaseline()).isFalse();
    }

    @Test
    void doesNotIssueTapPromotionWhenCutoffIsMissing() {
        prepareTapPromotionFixture(999L);
        when(tapThousandCompletionTrigger.issuingEnabled()).thenReturn(true);
        when(promotionPolicyConfig.tapThousandLaunchAt()).thenReturn(Optional.empty());

        tapBatchService.submitBatch(userId, new TapBatchSubmitRequest(sessionId, 1L, 100));

        verify(promotionGrantIssuer, never()).issueIfEligible(any(), any());
    }

    @Test
    void issuesTapPromotionOnlyOnTheBatchThatCrossesTheThreshold() {
        UserTapProgress progress = prepareTapPromotionFixture(0L);
        enableTapPromotion();

        // 기준값이 이 배치에서 0 으로 잡히고, 1,000 에 닿지 않으므로 아직 발급하지 않는다.
        tapBatchService.submitBatch(userId, new TapBatchSubmitRequest(sessionId, 1L, 100));
        assertThat(progress.hasPromotionTapBaseline()).isTrue();
        verify(promotionGrantIssuer, never()).issueIfEligible(any(), any());

        progress.addValidTaps(899L);

        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 2L))
                .thenReturn(Optional.empty());
        tapBatchService.submitBatch(userId, new TapBatchSubmitRequest(sessionId, 2L, 100));
        verify(promotionGrantIssuer, times(1)).issueIfEligible(eq(tapThousandCompletionTrigger), any());

        // 넘긴 뒤에는 다시 발급되지 않는다. 조회 없이 조건식만으로 걸러진다.
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 3L))
                .thenReturn(Optional.empty());
        tapBatchService.submitBatch(userId, new TapBatchSubmitRequest(sessionId, 3L, 100));
        verify(promotionGrantIssuer, times(1)).issueIfEligible(eq(tapThousandCompletionTrigger), any());
    }

    @Test
    void countsOnlyTapsAfterTheBaselineSoExistingUsersAreNotBackfilled() {
        UserTapProgress progress = prepareTapPromotionFixture(50_000L);
        enableTapPromotion();

        // 이미 5만 탭을 넘긴 기존 유저. 기준값이 잡히므로 순증은 0 에서 시작한다.
        tapBatchService.submitBatch(userId, new TapBatchSubmitRequest(sessionId, 1L, 100));

        verify(promotionGrantIssuer, never()).issueIfEligible(any(), any());
        assertThat(progress.getPromotionTapBaseline()).isEqualTo(50_000L);
    }

    private void enableTapPromotion() {
        when(tapThousandCompletionTrigger.issuingEnabled()).thenReturn(true);
        when(promotionPolicyConfig.tapThousandLaunchAt())
                .thenReturn(Optional.of(acceptedAt.minusSeconds(60)));
        when(promotionPolicyConfig.tapThousandThreshold()).thenReturn(1000);
    }

    private UserTapProgress prepareTapPromotionFixture(long alreadyTapped) {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L))
                .thenReturn(Optional.empty());
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate)))
                .thenReturn(UserTapDaily.createFor(user, tapDate));

        UserTapProgress progress = UserTapProgress.createFor(user, FAR_AWAY_TARGET);
        progress.addValidTaps(alreadyTapped);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchRepository.save(any(TapBatch.class))).thenReturn(savedBatch);
        return progress;
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

        when(userTapProgressService.drawNextTarget(eq(100L), eq(tapPolicyConfig))).thenReturn(400);


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
        verify(pointLedgerService).recordCredit(eq(pointAccount), eq(user), eq(1L), eq("TAP_REWARD"), any(UUID.class));
        // 계정은 루프 밖에서 한 번만 조회·저장한다.
        verify(pointAccountService).getForUser(userId);
        verify(pointAccountService).save(pointAccount);
        verify(pointAccountService, never()).credit(any(), anyLong());
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
        assertThat(boxAccount.getBoxBalance()).isEqualTo(1);
        assertThat(response.boxBalance()).isEqualTo(1);
        // 개봉 가능 여부는 이번 배치의 지급까지 반영된 잔고 기준이어야 한다.
        assertThat(response.canFreeOpen()).isTrue();
        assertThat(response.canAdOpen()).isTrue();
        // 계정은 루프 밖에서 한 번만 조회·저장한다.
        verify(keycapBoxAccountService).getForUser(userId);
        verify(keycapBoxAccountService).save(boxAccount);
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
        pointAccount.credit(7L);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(daily.getValidTapCount()).isEqualTo(100);
        assertThat(response.pointsAwarded()).isZero();
        assertThat(response.boxesDropped()).isZero();
        assertThat(response.balance()).isEqualTo(7L);
        assertThat(response.pointDailyCapReached()).isTrue();
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(pointAccountService, never()).save(any());
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

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 200);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.acceptedCount()).isEqualTo(200);
        assertThat(response.pointsAwarded()).isZero();
        assertThat(daily.getValidTapCount()).isEqualTo(3000);
        assertThat(daily.getTotalValidTapCount()).isEqualTo(3200);
        // 화면에 노출되는 값은 보상 상한에서 멈추지 않고 계속 증가해야 한다.
        assertThat(response.validTapCount()).isEqualTo(3200);
        verify(userTapDailyService).save(daily);
        // 진행도는 응답에 담기므로 항상 조회하지만, 지급이 없으면 저장하지 않는다.
        verify(userTapProgressService, never()).save(any());
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
        assertThat(boxAccount.getBoxBalance()).isEqualTo(1);
        verify(keycapBoxAccountService).save(boxAccount);
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(userTapSessionService).save(session);
    }

    @Test
    void returnsTodayStatusFieldsSoClientNeedsNoFollowUpQueries() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapPolicyConfig.pointDailyCap()).thenReturn(20);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);
        when(userTapProgressService.getForUser(userId)).thenReturn(UserTapProgress.createFor(user, 250));
        when(tapBatchRepository.save(any(TapBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTapSession session = UserTapSession.createFor(user, acceptedAt, acceptedAt.plusSeconds(3600), 300);
        when(userTapSessionService.getOrCreateActiveSession(eq(user), eq(acceptedAt), eq(tapPolicyConfig))).thenReturn(session);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 100);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        // 예전에는 이 값들을 채우려고 GET /tap/today 와 GET /keycap-boxes/status 를 뒤이어 호출해야 했다.
        assertThat(response.date()).isEqualTo(tapDate);
        assertThat(response.pointEarnedToday()).isZero();
        assertThat(response.remainingTapsToNextPoint()).isEqualTo(150);
        assertThat(response.remainingTapsToNextBox()).isEqualTo(200);
        assertThat(response.boxBalance()).isZero();
        assertThat(response.canFreeOpen()).isFalse();
        assertThat(response.canAdOpen()).isFalse();
        assertThat(response.charging()).isFalse();
        assertThat(response.nextRechargeAt()).isNull();
    }

    @Test
    void loadsAndSavesPointAccountOnceEvenWhenBatchAwardsMultiplePoints() {
        AppUser user = stubUser();
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L)).thenReturn(Optional.empty());
        when(tapPolicyConfig.pointDailyCap()).thenReturn(20);

        UserTapDaily daily = UserTapDaily.createFor(user, tapDate);
        when(userTapDailyService.getOrCreate(eq(user), eq(tapDate))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 10);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        TapBatch savedBatch = mock(TapBatch.class);
        when(savedBatch.getPublicId()).thenReturn(UUID.randomUUID());
        when(tapBatchRepository.save(any(TapBatch.class))).thenReturn(savedBatch);

        // 한 배치 안에서 목표를 세 번 넘기도록 다음 목표를 이어서 돌려준다.
        when(userTapProgressService.drawNextTarget(eq(30L), eq(tapPolicyConfig)))
                .thenReturn(20, 30, 40);

        TapBatchSubmitRequest request = new TapBatchSubmitRequest(sessionId, 1L, 30);
        TapBatchSubmitResponse response = tapBatchService.submitBatch(userId, request);

        assertThat(response.pointsAwarded()).isEqualTo(3);
        assertThat(response.balance()).isEqualTo(3L);
        assertThat(daily.getPointEarnedAmount()).isEqualTo(3);

        // 지급 횟수와 무관하게 계정 조회·저장은 각각 한 번이어야 한다.
        verify(pointAccountService, times(1)).getForUser(userId);
        verify(pointAccountService, times(1)).save(pointAccount);
        verify(pointAccountService, never()).credit(any(), anyLong());

        // 원장은 지급 건마다 남고 멱등 키는 서로 달라야 한다.
        ArgumentCaptor<UUID> keyCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(pointLedgerService, times(3))
                .recordCredit(eq(pointAccount), eq(user), eq(1L), eq("TAP_REWARD"), keyCaptor.capture());
        assertThat(keyCaptor.getAllValues()).doesNotHaveDuplicates();
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

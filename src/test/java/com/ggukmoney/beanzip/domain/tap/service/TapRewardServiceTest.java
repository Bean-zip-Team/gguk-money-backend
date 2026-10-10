package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.*;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassiveRoller;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.*;
import com.ggukmoney.beanzip.domain.promotion.service.*;
import com.ggukmoney.beanzip.domain.tap.entity.*;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TapRewardServiceTest {
    private final UserTapDailyService dailyService = mock(UserTapDailyService.class);
    private final UserTapProgressService progressService = mock(UserTapProgressService.class);
    private final UserTapSessionService sessionService = mock(UserTapSessionService.class);
    private final PointAccountService points = mock(PointAccountService.class);
    private final PointLedgerService ledger = mock(PointLedgerService.class);
    private final KeycapBoxAccountService wallets = mock(KeycapBoxAccountService.class);
    private final TapPolicyConfig tap = mock(TapPolicyConfig.class);
    private final TapRewardService rewards = new TapRewardService(dailyService, progressService, sessionService,
            points, ledger, wallets, tap, mock(PromotionPolicyConfig.class),
            mock(PromotionGrantIssuer.class), mock(TapThousandCompletionTrigger.class),
            mock(ApplicationEventPublisher.class),ZoneOffset.UTC);
    private final AppUser user = mock(AppUser.class);
    private final Instant now = Instant.parse("2026-10-10T03:00:00Z");
    private final UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.of(2026,10,10));
    private final UserTapProgress progress = UserTapProgress.createFor(user, 1);
    private final UserTapSession session = UserTapSession.createFor(user, now, now.plusSeconds(3600), 1000);
    private final PointAccount account = PointAccount.createFor(user);
    private final KeycapBoxAccount wallet = KeycapBoxAccount.createFor(user);

    private void prepare() {
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(dailyService.getOrCreate(any(),any())).thenReturn(daily);
        when(progressService.getForUser(any())).thenReturn(progress);
        when(sessionService.getOrCreateActiveSession(any(),any(),any())).thenReturn(session);
        when(points.getForUser(any())).thenReturn(account);
        when(wallets.getForUser(any())).thenReturn(wallet);
        when(tap.maxPerDay()).thenReturn(3000);
        when(tap.pointDailyCap()).thenReturn(150);
        when(tap.boxSessionIdleTimeoutSeconds()).thenReturn(3600);
        when(progressService.drawNextTarget(anyLong(),any())).thenAnswer(i -> (int)i.getArgument(0,Long.class).longValue()+1);
    }

    @Test void rewardsUseTheConfiguredBusinessDate() {
        prepare();
        rewards.award(user,Instant.parse("2026-10-09T16:00:00Z"),Effects.NONE,1,false,UUID.randomUUID(),new KeycapPassiveRoller());
        verify(dailyService).getOrCreate(user,LocalDate.of(2026,10,9));
    }

    @Test
    void actualCriticalCreditIsClippedAndEveryCrossedBoundaryConsumed() {
        prepare();
        daily.addPointEarned(149);
        var effects = new Effects(Critical.NONE, Critical.NONE, new Critical(1,5), 50,false,0,0,false);
        var result = rewards.award(user, now, effects, 3, false, UUID.randomUUID(), new KeycapPassiveRoller());
        assertThat(result.pointsAwarded()).isEqualTo(1);
        assertThat(daily.getPointEarnedAmount()).isEqualTo(150);
        assertThat(account.getBalance()).isEqualTo(1);
        assertThat(progress.getNextPointTarget()).isEqualTo(4);
        verify(ledger, times(1)).recordCredit(any(),any(),eq(1L),anyString(),any());
    }

    @Test
    void automaticAndBonusClicksConsumeAllowanceAndRankButExcludeMissions() {
        prepare();
        daily.addValidTaps(2998);
        rewards.award(user,now,Effects.NONE,2,true,UUID.randomUUID(),new KeycapPassiveRoller());
        var effects = new Effects(Critical.NONE,new Critical(1,5),Critical.NONE,50,false,0,0,false);
        var result = rewards.award(user,now,effects,3,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.effectiveCount()).isEqualTo(15);
        assertThat(daily.getValidTapCount()).isEqualTo(3000);
        assertThat(daily.getTotalValidTapCount()).isEqualTo(3);
        assertThat(daily.getTotalEffectiveTapCount()).isEqualTo(17);
        assertThat(progress.getCumulativeMissionTapCount()).isEqualTo(3);
        assertThat(progress.getCumulativeRankingTapCount()).isEqualTo(17);
    }

    @ParameterizedTest @ValueSource(booleans = {false,true})
    void eachManualOrAutomaticBatchHasOnePointBoundaryAtItsEndpoint(boolean automatic) {
        prepare();
        var result = rewards.award(user,now,Effects.NONE,100,automatic,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.pointsAwarded()).isEqualTo(1);
        assertThat(progress.getNextPointTarget()).isEqualTo(101);
        verify(progressService).drawNextTarget(eq(100L),eq(tap));
    }

    @Test void cappedPointBoundaryIsConsumedFromTheBatchEndpointWithoutCredit() {
        prepare();
        daily.addPointEarned(150);
        var result = rewards.award(user,now,Effects.NONE,100,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.pointsAwarded()).isZero();
        assertThat(progress.getNextPointTarget()).isEqualTo(101);
        verify(progressService).drawNextTarget(eq(100L),eq(tap));
        verifyNoInteractions(ledger);
    }

    @Test
    void eachCompletedShardUsesTheFollowingStepAndAwardsOnlyCrossedBoundaries() {
        prepare();
        session.resetFor(now,now.plusSeconds(3600),25);
        when(tap.boxSessionStep1()).thenReturn(25);
        when(tap.boxSessionStep2()).thenReturn(35);
        when(tap.boxSessionStep3()).thenReturn(50);
        when(sessionService.drawNextBoxTargetInSession(anyLong(),anyInt(),any())).thenCallRealMethod();
        var result = rewards.award(user,now,Effects.NONE,59,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.shardsDropped()).isEqualTo(1);
        assertThat(session.getNextBoxTarget()).isEqualTo(60);
    }
}

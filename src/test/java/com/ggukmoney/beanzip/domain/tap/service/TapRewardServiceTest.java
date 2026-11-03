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
            mock(ApplicationEventPublisher.class),ZoneOffset.UTC,
            new com.ggukmoney.beanzip.domain.keycap.passive.ClickCriticalSchedule("integration-test-secret-at-least-32-bytes-long"));
    private final AppUser user = mock(AppUser.class);
    private final Instant now = Instant.parse("2026-10-10T03:00:00Z");
    private final UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.of(2026,10,10));
    private final UserTapProgress progress = UserTapProgress.createFor(user, 1);
    private final UserTapSession session = UserTapSession.createFor(user, now, now.plusSeconds(3600), 1000);
    private final PointAccount account = PointAccount.createFor(user);
    private final KeycapBoxAccount wallet = KeycapBoxAccount.createFor(user, now);

    private void prepare() {
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(dailyService.getOrCreate(any(),any())).thenReturn(daily);
        when(progressService.getForUser(any())).thenReturn(progress);
        when(sessionService.getOrCreateActiveSession(any(),any(),any())).thenReturn(session);
        when(points.getForUser(any())).thenReturn(account);
        when(wallets.getForUserForUpdate(any())).thenReturn(wallet);
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

    @Test void cappedPointBoundaryIsPreservedForNextDayWithoutCredit() {
        prepare(); int originalTarget=progress.getNextPointTarget();
        daily.addPointEarned(150);
        var result=rewards.award(user,now,Effects.NONE,100,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.pointsAwarded()).isZero();
        assertThat(progress.getNextPointTarget()).isEqualTo(originalTarget);
        verify(progressService,never()).drawNextTarget(anyLong(),any());
        verifyNoInteractions(ledger);
    }

    @Test
    void eachCompletedShardAdvancesFromTheBatchEnd() {
        prepare();
        session.resetFor(now,now.plusSeconds(3600),25);
        when(tap.boxSessionStep1()).thenReturn(25);
        when(tap.boxSessionStep2()).thenReturn(35);
        when(tap.boxSessionStep3()).thenReturn(50);
        when(sessionService.drawNextBoxTargetInSession(anyLong(),anyInt(),any())).thenCallRealMethod();
        var result = rewards.award(user,now,Effects.NONE,59,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.shardsDropped()).isEqualTo(1);
        assertThat(session.getNextBoxTarget()).isEqualTo(84);
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void hundredManualOrAutomaticClicksKeep329ShardPacing(boolean automatic) {
        prepare();
        session.resetFor(now,now.plusSeconds(3600),25);
        when(tap.boxSessionStep1()).thenReturn(25);
        when(tap.boxSessionStep2()).thenReturn(35);
        when(tap.boxSessionStep3()).thenReturn(50);
        when(sessionService.drawNextBoxTargetInSession(anyLong(),anyInt(),any())).thenCallRealMethod();

        var result=rewards.award(user,now,Effects.NONE,100,automatic,UUID.randomUUID(),new KeycapPassiveRoller());

        assertThat(result.shardsDropped()).isEqualTo(1);
        assertThat(wallet.getShardBalance()).isEqualTo(1);
        assertThat(session.getNextBoxTarget()).isEqualTo(125);
        assertThat(session.getBoxesDroppedInSession()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(booleans = {false,true})
    void criticalsUseEffectiveBatchEndAndMultiplyOnlyTheActualDrop(boolean automatic) {
        prepare();
        session.resetFor(now,now.plusSeconds(3600),25);
        when(tap.boxSessionStep1()).thenReturn(25);
        when(tap.boxSessionStep2()).thenReturn(35);
        when(tap.boxSessionStep3()).thenReturn(50);
        when(sessionService.drawNextBoxTargetInSession(anyLong(),anyInt(),any())).thenCallRealMethod();
        var effects=new Effects(new Critical(1,2),new Critical(1,5),Critical.NONE,50,false,0,0,false);

        var result=rewards.award(user,now,effects,automatic?100:20,automatic,UUID.randomUUID(),new KeycapPassiveRoller());

        assertThat(result.effectiveCount()).isEqualTo(100);
        assertThat(result.shardsDropped()).isEqualTo(2);
        assertThat(wallet.getShardBalance()).isEqualTo(2);
        assertThat(session.getNextBoxTarget()).isEqualTo(125);
        assertThat(progress.getCumulativeRankingTapCount()).isEqualTo(100);
        assertThat(progress.getCumulativeMissionTapCount()).isEqualTo(automatic?0:20);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 30})
    void zeroAndPositiveAutomaticBatchesIncludeConvertedLegacyBalance(int count) {
        prepare(); wallet.addShards(9);
        when(wallets.getForUserForUpdate(any())).thenAnswer(i -> {wallet.addShards(3); return wallet;});
        var result=rewards.award(user,now,Effects.NONE,count,true,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.wallet().getShardBalance()).isEqualTo(12);
        assertThat(result.shardsDropped()).isZero();
        assertThat(progress.getCumulativeMissionTapCount()).isZero();
        if(count==0) assertThat(result.pointsAwarded()).isZero();
    }

    private void prepareOverflow(double probability) {
        prepare();
        when(tap.pointOverflowEnabled()).thenReturn(true);
        when(tap.pointOverflowProbability()).thenReturn(probability);
        when(tap.pointOverflowDailyCap()).thenReturn(10);
    }

    @Test void overflowIsOffByDefaultSoTapsPastTheTapCapEarnNothing() {
        prepare();
        daily.addValidTaps(3000); daily.addPointEarned(147);
        var result=rewards.award(user,now,Effects.NONE,100,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.pointsAwarded()).isZero();
        verifyNoInteractions(ledger);
    }

    @Test void tapsLeftUnpaidByTheTapCapRollOverflowUpToTheCombinedCeiling() {
        prepareOverflow(1.0);
        daily.addValidTaps(3000); daily.addPointEarned(147);
        var result=rewards.award(user,now,Effects.NONE,100,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.pointsAwarded()).isEqualTo(13);
        assertThat(daily.getPointEarnedAmount()).isEqualTo(160);
        assertThat(account.getBalance()).isEqualTo(13);
        verify(ledger).recordCredit(any(),any(),eq(13L),eq("TAP_OVERFLOW_REWARD"),any());
        verify(progressService,never()).drawNextTarget(anyLong(),any());
    }

    @Test void afterThePointCapEveryManualTapIsUnpaidAndRolls() {
        prepareOverflow(1.0);
        daily.addValidTaps(1000); daily.addPointEarned(150);
        var result=rewards.award(user,now,Effects.NONE,20,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.pointsAwarded()).isEqualTo(10);
        assertThat(daily.getPointEarnedAmount()).isEqualTo(160);
        var again=rewards.award(user,now,Effects.NONE,20,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(again.pointsAwarded()).isZero();
    }

    @Test void aBatchCrossingTheTapCapRollsOnlyTheTapsPastIt() {
        prepareOverflow(1.0);
        daily.addValidTaps(2990);
        var result=rewards.award(user,now,Effects.NONE,25,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(result.pointsAwarded()).isEqualTo(16);
        verify(ledger).recordCredit(any(),any(),eq(1L),eq("TAP_REWARD"),any());
        verify(ledger).recordCredit(any(),any(),eq(15L),eq("TAP_OVERFLOW_REWARD"),any());
    }

    @Test void overflowNeverRollsBeforeEitherCapClosesOrForAutomaticClicks() {
        prepareOverflow(1.0);
        daily.addValidTaps(1000); daily.addPointEarned(100);
        var open=rewards.award(user,now,Effects.NONE,50,false,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(open.pointsAwarded()).isEqualTo(1);
        daily.addValidTaps(1950); daily.addPointEarned(49);
        var automatic=rewards.award(user,now,Effects.NONE,500,true,UUID.randomUUID(),new KeycapPassiveRoller());
        assertThat(automatic.pointsAwarded()).isZero();
        verify(ledger,never()).recordCredit(any(),any(),anyLong(),eq("TAP_OVERFLOW_REWARD"),any());
    }
}

package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Effects;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassiveRoller;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.*;
import com.ggukmoney.beanzip.domain.promotion.service.*;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreSyncRequestedEvent;
import com.ggukmoney.beanzip.domain.tap.entity.*;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;

/** Called only after the shared user lock. The caller persists the request result in the same transaction. */
@Service
@RequiredArgsConstructor
public class TapRewardService {
    private final UserTapDailyService dailyService;
    private final UserTapProgressService progressService;
    private final UserTapSessionService sessionService;
    private final PointAccountService points;
    private final PointLedgerService ledger;
    private final KeycapBoxAccountService wallets;
    private final TapPolicyConfig tap;
    private final PromotionPolicyConfig promotion;
    private final PromotionGrantIssuer promotionIssuer;
    private final TapThousandCompletionTrigger thousandTrigger;
    private final ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.MANDATORY)
    public Award award(AppUser user, Instant now, Effects effects, int count, boolean automatic,
                       UUID operationId, KeycapPassiveRoller roller) {
        if (count < 0) throw new IllegalArgumentException("Negative click count");
        var daily = dailyService.getOrCreate(user, LocalDate.ofInstant(now, ZoneId.of("Asia/Seoul")));
        var progress = progressService.getForUser(user.getId());
        var session = sessionService.getOrCreateActiveSession(user, now, tap);
        var account = points.getForUser(user.getId());
        var wallet = wallets.getForUser(user.getId());
        if (count==0) return new Award(0,0,0,daily,progress,session,account,wallet);
        int eligible = Math.min(count, Math.max(tap.maxPerDay() - daily.getValidTapCount(), 0));
        var clicks = automatic ? new KeycapPassiveRoller.ClickResult(count,count,eligible)
                : roller.rollClicks(effects,count,eligible);
        if (!automatic) daily.addSubmittedTaps(count);
        daily.addEffectiveTaps(clicks.effectiveCount());
        daily.addValidTaps(eligible);
        progress.addRewardTaps(clicks.pointEligibleEffectiveCount());
        progress.addRankingTaps(clicks.effectiveCount());
        if (!automatic) {
            long before = progress.getCumulativeMissionTapCount();
            progress.addMissionTaps(eligible);
            issueMission(user, progress, before, now);
        }

        int awarded = 0;
        int index = 0;
        // Advance from each crossed boundary, not from the batch endpoint. Capped payouts consume boundaries too.
        while (progress.hasReachedPointTarget()) {
            int amount = roller.rollPoint(effects, 1, Math.max(tap.pointDailyCap()-daily.getPointEarnedAmount(),0));
            if (amount > 0) {
                account.credit(amount);
                UUID key = UUID.nameUUIDFromBytes((operationId+"-"+index).getBytes(StandardCharsets.UTF_8));
                ledger.recordCredit(account,user,amount,"TAP_REWARD",key);
                daily.addPointEarned(amount);
                awarded = Math.addExact(awarded,amount);
            }
            int boundary = progress.getNextPointTarget();
            int next = progressService.drawNextTarget(boundary,tap);
            if (next <= boundary) throw new IllegalStateException("Point target must advance");
            progress.advancePointTarget(next);
            index++;
        }
        int shards = 0;
        session.addValidTaps(clicks.effectiveCount());
        if (count > 0) session.recordActivity(now,tap.boxSessionIdleTimeoutSeconds());
        while (session.hasReachedBoxTarget()) {
            int amount = roller.rollShard(effects,1);
            wallet.addShards(amount);
            shards = Math.addExact(shards,amount);
            int boundary = session.getNextBoxTarget();
            int next = sessionService.drawNextBoxTargetInSession(boundary,session.getBoxesDroppedInSession(),tap);
            if (next <= boundary) throw new IllegalStateException("Shard target must advance");
            session.advanceBoxTarget(next);
        }
        dailyService.save(daily);
        progressService.save(progress);
        sessionService.save(session);
        if (awarded>0) points.save(account);
        if (shards>0) wallets.save(wallet);
        if (count>0) events.publishEvent(new RankingScoreSyncRequestedEvent(user.getId(),now));
        return new Award(clicks.effectiveCount(),awarded,shards,daily,progress,session,account,wallet);
    }

    private void issueMission(AppUser user, UserTapProgress progress, long before, Instant now) {
        if (!thousandTrigger.issuingEnabled()) return;
        var launch = promotion.tapThousandLaunchAt();
        if (launch.isEmpty() || now.isBefore(launch.get())) return;
        long baseline = progress.ensurePromotionTapBaseline(before);
        long after = progress.getCumulativeMissionTapCount();
        int threshold = promotion.tapThousandThreshold();
        if (before-baseline < threshold && after-baseline >= threshold)
            promotionIssuer.issueIfEligible(thousandTrigger,PromotionTriggerContext.tapThresholdCrossed(user,after-baseline,now));
    }

    public record Award(int effectiveCount, int pointsAwarded, int shardsDropped, UserTapDaily daily,
                        UserTapProgress progress, UserTapSession session, PointAccount account, KeycapBoxAccount wallet) {}
}

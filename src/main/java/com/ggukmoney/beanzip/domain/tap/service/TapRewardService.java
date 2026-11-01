package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.passive.ClickCriticalSchedule;
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
    private final ZoneId businessZoneId;
    private final ClickCriticalSchedule clickCriticals;

    @Transactional(propagation = Propagation.MANDATORY)
    public Award award(AppUser user, Instant now, Effects effects, int count, boolean automatic,
                       UUID operationId, KeycapPassiveRoller roller) {
        return award(user, now, effects, count, automatic, operationId, roller, 1);
    }

    /** {@code count} is in points; {@code pressSize} points make one manual press for click criticals. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Award award(AppUser user, Instant now, Effects effects, int count, boolean automatic,
                       UUID operationId, KeycapPassiveRoller roller, int pressSize) {
        if (count < 0) throw new IllegalArgumentException("Negative click count");
        var daily = dailyService.getOrCreate(user, LocalDate.ofInstant(now, businessZoneId));
        var progress = progressService.getForUser(user.getId());
        var session = sessionService.getOrCreateActiveSession(user, now, tap);
        var account = points.getForUser(user.getId());
        var wallet = wallets.getForUserForUpdate(user.getId());
        if (count==0) return new Award(0,0,0,0,daily,progress,session,account,wallet);
        int eligible = Math.min(count, Math.max(tap.maxPerDay() - daily.getValidTapCount(), 0));
        // Automatic clicks bypass axis ② and do not advance the manual tap numbers it is announced on.
        var clicks = automatic ? new ClickCriticalSchedule.ClickResult(count,count,eligible,0)
                : clickCriticals.roll(user.getId(),progress.getCumulativeMissionTapCount(),effects.click(),count,eligible,pressSize);
        if (!automatic) daily.addSubmittedTaps(count);
        daily.addEffectiveTaps(clicks.effectiveCount());
        daily.addValidTaps(eligible);
        progress.addRewardTaps(clicks.pointEligibleEffectiveCount());
        progress.addRankingTaps(clicks.effectiveCount());
        if (!automatic) {
            long before = progress.getCumulativeMissionTapCount();
            progress.addMissionTaps(count);
            issueMission(user, progress, before, now);
        }

        int awarded = 0;
        boolean pointCapClosedBefore = daily.getPointEarnedAmount() >= tap.pointDailyCap();
        // Manual and automatic batches use the same legacy pacing: one payout, then a target beyond the batch.
        // An already exhausted daily cap preserves the reached target for the next day.
        if (daily.getPointEarnedAmount()<tap.pointDailyCap() && progress.hasReachedPointTarget()) {
            int amount = roller.rollPoint(effects, 1, Math.max(tap.pointDailyCap()-daily.getPointEarnedAmount(),0));
            if (amount > 0) {
                account.credit(amount);
                UUID key = UUID.nameUUIDFromBytes((operationId+"-0").getBytes(StandardCharsets.UTF_8));
                ledger.recordCredit(account,user,amount,"TAP_REWARD",key);
                daily.addPointEarned(amount);
                awarded = Math.addExact(awarded,amount);
            }
            long batchEnd = progress.getCumulativeValidTapCount();
            int next = progressService.drawNextTarget(batchEnd,tap);
            if (next <= batchEnd) throw new IllegalStateException("Point target must advance");
            progress.advancePointTarget(next);
        }
        // BEA-360: once either daily cap has closed the normal payout, every manual tap it left unpaid
        // gets a rare independent 1P trial. The tap cap (3,000) closes first on most days, so taps past
        // `eligible` count as unpaid; after the point cap the whole batch does. Automatic clicks never roll.
        // The day's hard ceiling is pointDailyCap + overflowDailyCap, enforced on point_earned_amount alone.
        if (!automatic && tap.pointOverflowEnabled()) {
            int unpaid = pointCapClosedBefore ? count : count - eligible;
            int remaining = Math.max(tap.pointDailyCap() + tap.pointOverflowDailyCap() - daily.getPointEarnedAmount(), 0);
            int hits = unpaid > 0 && remaining > 0 ? roller.rollOverflowHits(unpaid, tap.pointOverflowProbability()) : 0;
            if (hits > 0) {
                int amount = roller.rollPoint(effects, hits, remaining);
                if (amount > 0) {
                    account.credit(amount);
                    UUID key = UUID.nameUUIDFromBytes((operationId+"-overflow").getBytes(StandardCharsets.UTF_8));
                    ledger.recordCredit(account,user,amount,"TAP_OVERFLOW_REWARD",key);
                    daily.addPointEarned(amount);
                    awarded = Math.addExact(awarded,amount);
                }
            }
        }
        int shards = 0;
        session.addValidTaps(clicks.effectiveCount());
        if (count > 0) session.recordActivity(now,tap.boxSessionIdleTimeoutSeconds());
        while (session.hasReachedBoxTarget()) {
            int amount = roller.rollShard(effects,1);
            wallet.addShards(amount);
            shards = Math.addExact(shards,amount);
            long batchEnd = session.getSessionValidTapCount();
            int next = sessionService.drawNextBoxTargetInSession(batchEnd,session.getBoxesDroppedInSession(),tap);
            if (next <= batchEnd) throw new IllegalStateException("Shard target must advance beyond the batch");
            session.advanceBoxTarget(next);
        }
        dailyService.save(daily);
        progressService.save(progress);
        sessionService.save(session);
        if (awarded>0) points.save(account);
        if (shards>0) wallets.save(wallet);
        if (count>0) events.publishEvent(new RankingScoreSyncRequestedEvent(user.getId(),now));
        return new Award(clicks.effectiveCount(),clicks.criticalCount(),awarded,shards,daily,progress,session,account,wallet);
    }

    public int availableShardBalance(UUID userId) { return wallets.getAvailableShardBalance(userId); }

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

    public record Award(int effectiveCount, int clickCriticalCount, int pointsAwarded, int shardsDropped, UserTapDaily daily,
                        UserTapProgress progress, UserTapSession session, PointAccount account, KeycapBoxAccount wallet) {}
}

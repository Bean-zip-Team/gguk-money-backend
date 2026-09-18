package com.ggukmoney.beanzip.domain.ranking.boost;

import com.ggukmoney.beanzip.domain.ranking.entity.*;
import com.ggukmoney.beanzip.domain.ranking.repository.*;
import com.ggukmoney.beanzip.domain.ranking.service.*;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreChangedEvent;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryPersistenceService;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemRankingBoostTransactionService {
    private final SystemRankingBoostPolicy policy;
    private final SystemRankingBoostRolloutGate gate;
    private final RankingBoostRunRepository runs;
    private final RankingSeasonService seasons;
    private final RankingSeasonLockRepository seasonLock;
    private final RankingProperties properties;
    private final RankingEntryRepository entries;
    private final AppUserRepository users;
    private final RankingBoostRandom random;
    private final NotificationDeliveryPersistenceService notifications;
    private final ApplicationEventPublisher events;

    /** Commit the chosen time separately, so an application rollback never rerolls today's slot. */
    @Transactional
    public Optional<RankingBoostRun> plan(Instant now) {
        Optional<SystemRankingBoostPolicy.Snapshot> loaded = policy.load(now);
        Optional<RankingSeason> active = seasons.findActiveWeeklySeason();
        if (loaded.isEmpty() || !loaded.get().enabled() || active.isEmpty()
                || !gate.permits(now, active.get())) return Optional.empty();
        seasonLock.acquireWeeklyRankingTransactionLock(properties.weeklyAdvisoryLockKey());
        loaded = policy.load(now);
        active = seasons.findActiveWeeklySeason();
        if (loaded.isEmpty() || !loaded.get().enabled() || active.isEmpty()
                || !active.get().contains(now) || !gate.permits(now, active.get())) return Optional.empty();
        LocalDate date = now.atZone(RankingBoostRandom.KST).toLocalDate();
        return Optional.of(runs.findDateForUpdate(date)
                .orElseGet(() -> runs.saveAndFlush(RankingBoostRun.plan(date, random.scheduledAt(date), now))));
    }

    @Transactional
    public Optional<RankingBoostRun> advance(Instant now) {
        // Early stop avoids DB audit writes while rollout is closed or kill switch is off.
        Optional<SystemRankingBoostPolicy.Snapshot> initial = policy.load(now);
        Optional<RankingSeason> initialSeason = seasons.findActiveWeeklySeason();
        if (initial.isEmpty() || !initial.get().enabled() || initialSeason.isEmpty()
                || !gate.permits(now, initialSeason.get())) return Optional.empty();
        seasonLock.acquireWeeklyRankingTransactionLock(properties.weeklyAdvisoryLockKey());
        Optional<SystemRankingBoostPolicy.Snapshot> loaded = policy.load(now);
        Optional<RankingSeason> active = seasons.findActiveWeeklySeason();
        if (loaded.isEmpty() || !loaded.get().enabled() || active.isEmpty()) return Optional.empty();
        RankingSeason season = active.get();
        if (!season.contains(now) || !gate.permits(now, season)) return Optional.empty();
        var snapshot = loaded.get();
        LocalDate date = now.atZone(RankingBoostRandom.KST).toLocalDate();
        Optional<RankingBoostRun> planned = runs.findDateForUpdate(date);
        if (planned.isEmpty()) return Optional.empty();
        RankingBoostRun run = planned.get();
        if (run.getStatus() != RankingBoostRun.Status.PLANNED) return Optional.of(run);
        Instant start = date.atTime(18, 0).atZone(RankingBoostRandom.KST).toInstant();
        Instant end = date.atTime(22, 0).atZone(RankingBoostRandom.KST).toInstant();
        if (!now.isBefore(end)) return skipped(run, "WINDOW_EXPIRED");
        if (now.isBefore(start) || now.isBefore(run.getScheduledAt())) return Optional.of(run);
        Set<UUID> excluded = new HashSet<>(snapshot.internalUserIds());
        excluded.addAll(runs.findSeasonRecipients(season.getId()));
        Optional<RankingEntryRepository.RankingParticipantRow> leader = entries.findRealLeader(season.getId(), excluded);
        if (leader.isEmpty()) return skipped(run, "NO_REAL_LEADER");
        // Native sampling and subsequent rank counts must use the same leader score.
        // Only this daily boost locks the leader; ordinary tap projections remain concurrent.
        RankingEntry lockedLeader = entries.findLeaderEntryForUpdate(season, leader.get().userId())
                .orElseThrow(() -> new OptimisticLockingFailureException("ranking boost leader disappeared; retry daily plan"));
        if (lockedLeader.getScore() != leader.get().score() || lockedLeader.getRankingBoostScore() != 0
                || !lockedLeader.isParticipantEligible()) {
            throw new OptimisticLockingFailureException("ranking boost leader changed; retry daily plan");
        }
        if (leader.get().score() < snapshot.minimumLeaderScore()) return skipped(run, "LEADER_BELOW_MINIMUM");
        List<AppUser> candidates = users.findAllById(snapshot.internalUserIds()).stream()
                .filter(user -> user.getStatus() == AppUser.Status.ACTIVE).toList();
        if (candidates.isEmpty()) return skipped(run, "NO_ACTIVE_INTERNAL_USER");
        Map<UUID, RankingEntry> byUser = entries.findEntriesByUserIds(season, snapshot.internalUserIds()).stream()
                .collect(Collectors.toMap(entry -> entry.getUser().getId(), Function.identity()));
        AppUser selected = candidates.stream().min(Comparator
                .comparingLong((AppUser user) -> Optional.ofNullable(byUser.get(user.getId())).map(RankingEntry::getScore).orElse(0L))
                .thenComparing(user -> user.getId().toString())).orElseThrow();
        RankingEntry entry = byUser.getOrDefault(selected.getId(),
                RankingEntry.createFor(season, selected, 0L, null, now));
        long previous = entry.getScore();
        if (previous > leader.get().score()) return skipped(run, "SELECTED_ALREADY_AHEAD");
        int increment = random.increment(snapshot.minIncrement(), snapshot.maxIncrement());
        long target = Math.addExact(leader.get().score(), increment);
        long rankBefore = entries.countParticipantsAhead(season, leader.get().score(), leader.get().userId().toString()) + 1;
        long real = entry.getRealScore();
        long boostBefore = entry.getRankingBoostScore();
        String previousRegion = entry.getRegionCode();
        entry.boostTo(target, now);
        entries.saveAndFlush(entry);
        long rankAfter = entries.countParticipantsAhead(season, leader.get().score(), leader.get().userId().toString()) + 1;
        Long deliveryId = notifications.prepareSystemRankingBoost(leader.get().userId(), season.getId(), date, rankBefore, rankAfter)
                .map(NotificationDelivery::getId).orElse(null);
        // Native delivery insert clears persistence context: save audit explicitly afterwards.
        run.applied(season.getId(), selected.getId(), leader.get().userId(), real, previous, target,
                boostBefore, entry.getRankingBoostScore(), increment, rankBefore, rankAfter,
                policy.serialize(snapshot), now, deliveryId);
        runs.saveAndFlush(run);
        events.publishEvent(new RankingScoreChangedEvent(season.getId(), selected.getId(), entry.getScore(),
                entry.getRegionCode(), previousRegion, entry.isParticipantEligible(), now));
        log.info("SYSTEM_RANKING_BOOST applied runId={} date={} seasonId={} userId={} scoreBefore={} scoreAfter={}",
                run.getId(), date, season.getId(), selected.getId(), previous, target);
        return Optional.of(run);
    }

    /** Claim BEFORE HTTP, but never hold a DB transaction across the provider call. */
    @Transactional
    public Optional<Long> claimDispatch(LocalDate date, Instant now) {
        if (!date.equals(now.atZone(RankingBoostRandom.KST).toLocalDate())
                || !now.isBefore(date.atTime(22, 0).atZone(RankingBoostRandom.KST).toInstant())) return Optional.empty();
        Optional<SystemRankingBoostPolicy.Snapshot> loaded = policy.load(now);
        Optional<RankingSeason> active = seasons.findActiveWeeklySeason();
        if (loaded.isEmpty() || !loaded.get().enabled() || active.isEmpty()
                || !gate.permits(now, active.get())) return Optional.empty();
        seasonLock.acquireWeeklyRankingTransactionLock(properties.weeklyAdvisoryLockKey());
        loaded = policy.load(now);
        active = seasons.findActiveWeeklySeason();
        if (loaded.isEmpty() || !loaded.get().enabled() || active.isEmpty()
                || !active.get().contains(now) || !gate.permits(now, active.get())) return Optional.empty();
        Optional<RankingBoostRun> found = runs.findDateForUpdate(date);
        if (found.isEmpty()) return Optional.empty();
        RankingBoostRun run = found.get();
        if (run.getStatus() != RankingBoostRun.Status.APPLIED || run.getNotificationDeliveryId() == null
                || run.getDispatchClaimedAt() != null || !active.get().getId().equals(run.getSeasonId())
                || loaded.get().internalUserIds().contains(run.getLeaderUserId())
                || runs.findSeasonRecipients(run.getSeasonId()).contains(run.getLeaderUserId())
                || !notifications.canDispatchRankChange(run.getLeaderUserId(), now)
                || notifications.findPendingRankChange(run.getNotificationDeliveryId()).isEmpty()) return Optional.empty();
        if (!run.claimDispatch(now)) return Optional.empty();
        runs.saveAndFlush(run);
        return Optional.of(run.getNotificationDeliveryId());
    }

    private Optional<RankingBoostRun> skipped(RankingBoostRun run, String reason) {
        run.skip(reason);
        log.info("SYSTEM_RANKING_BOOST skipped date={} reason={}", run.getRunDate(), reason);
        return Optional.of(runs.saveAndFlush(run));
    }
}

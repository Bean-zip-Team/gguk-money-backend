package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreChangedEvent;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.service.UserTapProgressService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankingProjectionService {

    private final RankingSeasonService seasonService;
    private final RankingEntryRepository entryRepository;
    private final UserService userService;
    private final UserTapDailyRepository userTapDailyRepository;
    private final UserTapProgressService userTapProgressService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ZoneId businessZoneId;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<RankingEntry> syncLatestWeeklyScore(UUID userId, Instant occurredAt) {
        return seasonService.findWeeklySeasonContaining(occurredAt)
                .flatMap(season -> syncWeeklyScore(season, userId, weeklyScore(userId, season), occurredAt, true));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<RankingEntry> syncWeeklyScore(
            RankingSeason season,
            UUID userId,
            long score,
            Instant occurredAt,
            boolean publishRedisEvent
    ) {
        if (season == null
                || !season.isWeekly()
                || season.getStatus() != RankingSeasonStatus.ACTIVE
                || !season.contains(occurredAt)) {
            return Optional.empty();
        }
        return upsertScore(season, userId, score, occurredAt, publishRedisEvent);
    }

    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RankingEntry syncLatestAllTimeScore(UUID userId) {
        UserTapProgress progress = userTapProgressService.getForUser(userId);
        return upsertAllTimeScore(userId, progress.getCumulativeValidTapCount());
    }

    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RankingEntry syncAllTimeScore(UUID userId, long cumulativeValidTapCount) {
        return upsertAllTimeScore(userId, cumulativeValidTapCount);
    }

    private RankingEntry upsertAllTimeScore(UUID userId, long cumulativeValidTapCount) {
        RankingSeason season = getOrCreateActiveAllTimeSeason();
        AppUser user = userService.getById(userId);
        Instant now = clock.instant();
        RankingEntry entry = entryRepository.findBySeasonAndUserId(season, userId)
                .orElseGet(() -> RankingEntry.createFor(season, user, cumulativeValidTapCount, null, now));
        String previousRegionCode = entry.getRegionCode();
        long score = Math.max(entry.getScore(), cumulativeValidTapCount);
        if (!entry.getScore().equals(score) || entry.getRegionCode() != null) {
            entry.updateScore(score, null, now);
        }
        RankingEntry saved = entryRepository.save(entry);
        eventPublisher.publishEvent(new RankingScoreChangedEvent(
                season.getId(),
                userId,
                saved.getScore(),
                saved.getRegionCode(),
                previousRegionCode,
                saved.isParticipantEligible(),
                now
        ));
        return saved;
    }

    private Optional<RankingEntry> upsertScore(
            RankingSeason season,
            UUID userId,
            long score,
            Instant occurredAt,
            boolean publishRedisEvent
    ) {
        AppUser user = userService.getById(userId);
        RankingEntry entry = entryRepository.findBySeasonAndUserId(season, userId)
                .orElseGet(() -> score <= 0 ? null : RankingEntry.createFor(season, user, score, null, occurredAt));
        if (entry == null) {
            return Optional.empty();
        }
        String previousRegionCode = entry.getRegionCode();
        if (!entry.getScore().equals(score) || entry.getRegionCode() != null) {
            entry.updateScore(score, null, occurredAt);
        }
        RankingEntry saved = entryRepository.save(entry);
        if (publishRedisEvent) {
            eventPublisher.publishEvent(new RankingScoreChangedEvent(
                    season.getId(),
                    userId,
                    saved.getScore(),
                    saved.getRegionCode(),
                    previousRegionCode,
                    saved.isParticipantEligible(),
                    occurredAt
            ));
        }
        return Optional.of(saved);
    }

    private long weeklyScore(UUID userId, RankingSeason season) {
        return userTapDailyRepository.sumValidTapCount(
                userId,
                LocalDate.ofInstant(season.getStartsAt(), businessZoneId),
                LocalDate.ofInstant(season.getEndsAt(), businessZoneId)
        );
    }

    private RankingSeason getOrCreateActiveAllTimeSeason() {
        return seasonService.getOrCreateActiveAllTimeSeason();
    }
}

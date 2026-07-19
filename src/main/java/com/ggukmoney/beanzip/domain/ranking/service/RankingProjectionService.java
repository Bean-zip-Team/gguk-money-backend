package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreChangedEvent;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankingProjectionService {

    private final RankingSeasonService seasonService;
    private final RankingEntryRepository entryRepository;
    private final UserService userService;
    private final UserTapProgressService userTapProgressService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RankingEntry syncLatestAllTimeScore(UUID userId) {
        UserTapProgress progress = userTapProgressService.getForUser(userId);
        return upsertAllTimeScore(userId, progress.getCumulativeValidTapCount());
    }

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

    private RankingSeason getOrCreateActiveAllTimeSeason() {
        return seasonService.getOrCreateActiveAllTimeSeason();
    }
}

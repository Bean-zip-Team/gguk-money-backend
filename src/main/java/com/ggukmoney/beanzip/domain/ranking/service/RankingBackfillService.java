package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingBackfillService {

    private final UserTapDailyRepository userTapDailyRepository;
    private final UserTapProgressRepository userTapProgressRepository;
    private final RankingEntryRepository rankingEntryRepository;
    private final RankingProjectionService rankingProjectionService;
    private final UserService userService;
    private final RankingProperties properties;
    private final Clock clock;
    private final ZoneId businessZoneId;

    @Transactional
    public long backfillActiveWeeklySeason(RankingSeason season) {
        LocalDate startDate = LocalDate.ofInstant(season.getStartsAt(), businessZoneId);
        LocalDate endDate = LocalDate.ofInstant(season.getEndsAt(), businessZoneId);
        long processed = 0;
        String lastUserId = null;
        while (true) {
            List<UserTapDailyRepository.UserTapAggregateProjection> rows =
                    userTapDailyRepository.findValidTapAggregates(startDate, endDate, lastUserId, properties.pageSize());
            if (rows.isEmpty()) {
                return processed;
            }
            for (UserTapDailyRepository.UserTapAggregateProjection row : rows) {
                rankingProjectionService.syncWeeklyScore(
                        season,
                        row.getUserId(),
                        row.getScore(),
                        clock.instant(),
                        false
                );
                lastUserId = row.getUserId().toString();
                processed++;
            }
            if (rows.size() < properties.pageSize()) {
                return processed;
            }
        }
    }

    @Transactional
    public long backfillFinalizingWeeklySeason(
            RankingSeason targetSeason,
            LocalDate startDate,
            LocalDate endDate
    ) {
        long processed = 0;
        String lastUserId = null;
        Instant occurredAt = clock.instant();
        rankingEntryRepository.resetScoresWithoutWeeklyAggregate(
                targetSeason.getId(),
                startDate,
                endDate,
                occurredAt
        );
        while (true) {
            List<UserTapDailyRepository.UserTapAggregateProjection> rows =
                    userTapDailyRepository.findValidTapAggregates(startDate, endDate, lastUserId, properties.pageSize());
            if (rows.isEmpty()) {
                return processed;
            }
            for (UserTapDailyRepository.UserTapAggregateProjection row : rows) {
                upsertFinalizingScore(targetSeason, row.getUserId(), row.getScore(), occurredAt);
                lastUserId = row.getUserId().toString();
                processed++;
            }
            if (rows.size() < properties.pageSize()) {
                return processed;
            }
        }
    }

    private void upsertFinalizingScore(RankingSeason targetSeason, java.util.UUID userId, long score, Instant occurredAt) {
        AppUser user = userService.getById(userId);
        RankingEntry entry = rankingEntryRepository.findBySeasonAndUserId(targetSeason, userId)
                .orElseGet(() -> RankingEntry.createFor(targetSeason, user, score, null, occurredAt));
        if (!entry.getScore().equals(score) || entry.getRegionCode() != null) {
            entry.updateScore(score, null, occurredAt);
        }
        rankingEntryRepository.save(entry);
    }

    @Deprecated
    @Transactional
    public long backfillActiveAllTimeFromTapProgress() {
        long processed = 0;
        int page = 0;
        while (true) {
            List<UserTapProgress> progressList = userTapProgressRepository.findActivePositiveProgress(
                    PageRequest.of(page, properties.pageSize())
            );
            if (progressList.isEmpty()) {
                return processed;
            }
            for (UserTapProgress progress : progressList) {
                rankingProjectionService.syncAllTimeScore(
                        progress.getUser().getId(),
                        progress.getCumulativeValidTapCount()
                );
                processed++;
            }
            if (progressList.size() < properties.pageSize()) {
                return processed;
            }
            page++;
        }
    }
}

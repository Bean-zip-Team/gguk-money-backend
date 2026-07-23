package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingBackfillService {

    private final UserTapDailyRepository userTapDailyRepository;
    private final UserTapProgressRepository userTapProgressRepository;
    private final RankingProjectionService rankingProjectionService;
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

package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingBackfillServiceTest {

    private final UserTapDailyRepository dailyRepository = mock(UserTapDailyRepository.class);
    private final UserTapProgressRepository progressRepository = mock(UserTapProgressRepository.class);
    private final RankingProjectionService projectionService = mock(RankingProjectionService.class);
    private final RankingProperties properties = new RankingProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T15:00:00Z"), ZoneOffset.UTC);
    private final ZoneId businessZoneId = ZoneId.of("Asia/Seoul");
    private final RankingBackfillService service = new RankingBackfillService(
            dailyRepository, progressRepository, projectionService, properties, clock, businessZoneId
    );

    @Test
    void backfillsWeeklySeasonFromDailyAggregateRows() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        UserTapDailyRepository.UserTapAggregateProjection row = aggregateRow(userId, 123L);
        when(dailyRepository.findValidTapAggregates(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27),
                null,
                properties.pageSize()
        )).thenReturn(List.of(row))
                .thenReturn(List.of());

        service.backfillActiveWeeklySeason(season);

        verify(projectionService).syncWeeklyScore(season, userId, 123L, clock.instant(), false);
    }

    @Test
    void backfillsOnlyRowsProvidedByActivePositiveProgressQuery() {
        UUID userId = UUID.randomUUID();
        UserTapProgress progress = mock(UserTapProgress.class);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(progress.getUser()).thenReturn(user);
        when(progress.getCumulativeValidTapCount()).thenReturn(123L);
        when(progressRepository.findActivePositiveProgress(PageRequest.of(0, properties.pageSize())))
                .thenReturn(List.of(progress));

        service.backfillActiveAllTimeFromTapProgress();

        verify(projectionService).syncAllTimeScore(userId, 123L);
    }

    @Test
    void backfillCanRunAgainBecauseProjectionSetsCumulativeScore() {
        UUID userId = UUID.randomUUID();
        UserTapProgress progress = mock(UserTapProgress.class);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(progress.getUser()).thenReturn(user);
        when(progress.getCumulativeValidTapCount()).thenReturn(123L);
        when(progressRepository.findActivePositiveProgress(PageRequest.of(0, properties.pageSize())))
                .thenReturn(List.of(progress))
                .thenReturn(List.of(progress));

        service.backfillActiveAllTimeFromTapProgress();
        service.backfillActiveAllTimeFromTapProgress();

        verify(projectionService, org.mockito.Mockito.times(2)).syncAllTimeScore(userId, 123L);
    }

    private UserTapDailyRepository.UserTapAggregateProjection aggregateRow(UUID userId, long score) {
        UserTapDailyRepository.UserTapAggregateProjection projection =
                mock(UserTapDailyRepository.UserTapAggregateProjection.class);
        when(projection.getUserId()).thenReturn(userId);
        when(projection.getScore()).thenReturn(score);
        return projection;
    }
}

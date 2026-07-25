package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingBackfillServiceTest {

    private final UserTapDailyRepository dailyRepository = mock(UserTapDailyRepository.class);
    private final UserTapProgressRepository progressRepository = mock(UserTapProgressRepository.class);
    private final RankingEntryRepository entryRepository = mock(RankingEntryRepository.class);
    private final RankingProjectionService projectionService = mock(RankingProjectionService.class);
    private final UserService userService = mock(UserService.class);
    private final RankingProperties properties = new RankingProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T15:00:00Z"), ZoneOffset.UTC);
    private final ZoneId businessZoneId = ZoneId.of("Asia/Seoul");
    private final RankingBackfillService service = new RankingBackfillService(
            dailyRepository, progressRepository, entryRepository, projectionService, userService, properties, clock, businessZoneId
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
    void backfillsFinalizingSeasonWithoutRedisScoreEvent() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(user.getStatus()).thenReturn(AppUser.Status.ACTIVE);
        RankingSeason season = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        org.springframework.test.util.ReflectionTestUtils.setField(season, "id", 1L);
        season.startFinalizing();
        UserTapDailyRepository.UserTapAggregateProjection row = aggregateRow(userId, 80L);
        when(dailyRepository.findValidTapAggregates(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27),
                null,
                properties.pageSize()
        )).thenReturn(List.of(row));
        when(userService.getById(userId)).thenReturn(user);
        when(entryRepository.findBySeasonAndUserId(season, userId)).thenReturn(java.util.Optional.empty());
        when(entryRepository.save(org.mockito.ArgumentMatchers.any(RankingEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.backfillFinalizingWeeklySeason(season, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27));

        verify(entryRepository).resetScoresWithoutWeeklyAggregate(
                1L,
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27),
                clock.instant()
        );
        verify(entryRepository).save(org.mockito.ArgumentMatchers.argThat(entry -> entry.getScore().equals(80L)));
        verify(projectionService, never()).syncWeeklyScore(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean()
        );
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

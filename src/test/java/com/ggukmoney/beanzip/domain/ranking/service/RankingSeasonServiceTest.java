package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingType;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonLockRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingSeasonServiceTest {

    private final RankingSeasonRepository seasonRepository = mock(RankingSeasonRepository.class);
    private final RankingSeasonLockRepository seasonLockRepository = mock(RankingSeasonLockRepository.class);
    private final RankingEntryRepository entryRepository = mock(RankingEntryRepository.class);
    private final RankingBackfillService backfillService = mock(RankingBackfillService.class);
    private final ObjectProvider<RankingBackfillService> backfillServiceProvider = mock(ObjectProvider.class);
    private final RankingProperties properties = new RankingProperties();
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T01:00:00Z"), ZoneOffset.UTC);
    private final ZoneId businessZoneId = ZoneId.of("Asia/Seoul");
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final RankingSeasonService service = new RankingSeasonService(
            seasonRepository, seasonLockRepository, entryRepository, properties, backfillServiceProvider,
            transactionManager, clock, businessZoneId, eventPublisher
    );

    RankingSeasonServiceTest() {
        when(backfillServiceProvider.getObject()).thenReturn(backfillService);
    }

    @Test
    void findsOnlyActiveAllTimeSeason() {
        RankingSeason active = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        when(seasonRepository.findByCodeAndStatus("ALL_TIME", RankingSeasonStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        assertThat(service.findActiveAllTimeSeason()).containsSame(active);
    }

    @Test
    void doesNotTreatClosedAllTimeSeasonAsCurrent() {
        when(seasonRepository.findByCodeAndStatus("ALL_TIME", RankingSeasonStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(service::getActiveAllTimeSeason)
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("RANKING_SEASON_NOT_FOUND");
    }

    @Test
    void createsSeasonInRequiresNewTransactionAndRequeriesAfterUniqueConflict() {
        RankingSeason active = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(seasonRepository.findByCodeAndStatus("ALL_TIME", RankingSeasonStatus.ACTIVE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(active));
        when(seasonRepository.saveAndFlush(any(RankingSeason.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        RankingSeason result = service.getOrCreateActiveAllTimeSeason();

        assertThat(result).isSameAs(active);
        verify(transactionManager).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW
        ));
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void createsCurrentWeeklySeasonForSeoulBusinessWeek() {
        Instant now = Instant.parse("2026-07-20T15:00:00Z");
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.ACTIVE))
                .thenReturn(List.of());
        when(seasonRepository.findByCode("WEEKLY_20260720")).thenReturn(Optional.empty());
        when(seasonRepository.saveAndFlush(any(RankingSeason.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RankingSeason result = service.ensureCurrentWeeklySeason(now);

        assertThat(result.getCode()).isEqualTo("WEEKLY_20260720");
        assertThat(result.getStartsAt()).isEqualTo(Instant.parse("2026-07-19T15:00:00Z"));
        assertThat(result.getEndsAt()).isEqualTo(Instant.parse("2026-07-26T15:00:00Z"));
        assertThat(result.getRankingType()).isEqualTo(RankingType.WEEKLY);
        assertThat(result.getStatus()).isEqualTo(RankingSeasonStatus.ACTIVE);
        verify(seasonLockRepository).acquireWeeklyRankingTransactionLock(properties.weeklyAdvisoryLockKey());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void keepsSameWeeklyCodeUntilNextSeoulMondayBoundary() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        RankingSeason existing = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        when(seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.ACTIVE))
                .thenReturn(List.of(existing));
        when(seasonRepository.findByCode("WEEKLY_20260720")).thenReturn(Optional.of(existing));

        RankingSeason result = service.ensureCurrentWeeklySeason(Instant.parse("2026-07-26T14:59:59Z"));

        assertThat(result).isSameAs(existing);
        assertThat(result.getCode()).isEqualTo("WEEKLY_20260720");
    }

    @Test
    void advancesWeeklyCodeAtExactSeoulMondayBoundaryAndFinalizesExpiredActiveSeason() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        RankingSeason expired = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        when(seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.ACTIVE))
                .thenReturn(List.of(expired));
        when(seasonRepository.findByCode("WEEKLY_20260727")).thenReturn(Optional.empty());
        when(seasonRepository.saveAndFlush(any(RankingSeason.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RankingSeason result = service.ensureCurrentWeeklySeason(Instant.parse("2026-07-26T15:00:00Z"));

        assertThat(expired.getStatus()).isEqualTo(RankingSeasonStatus.FINALIZING);
        assertThat(result.getCode()).isEqualTo("WEEKLY_20260727");
        assertThat(result.getStartsAt()).isEqualTo(Instant.parse("2026-07-26T15:00:00Z"));
        assertThat(result.getEndsAt()).isEqualTo(Instant.parse("2026-08-02T15:00:00Z"));
    }

    @Test
    void rejectsUnsupportedWeeklyResetTime() {
        assertThatThrownBy(() -> properties.setWeeklyResetTime("01:00"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("00:00");
    }

    @Test
    void getActiveWeeklySeasonDoesNotCreateOrLock() {
        RankingSeason active = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        when(seasonRepository.findByRankingTypeAndStatus(RankingType.WEEKLY, RankingSeasonStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        RankingSeason result = service.getActiveWeeklySeason();

        assertThat(result).isSameAs(active);
        verify(seasonRepository).findByRankingTypeAndStatus(RankingType.WEEKLY, RankingSeasonStatus.ACTIVE);
        verify(seasonLockRepository, org.mockito.Mockito.never()).acquireWeeklyRankingTransactionLock(any(Long.class));
    }

    @Test
    void rolloverAtEndsAtMovesExpiredActiveToFinalizingAndCreatesCurrentWeek() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        RankingSeason expired = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        when(seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.ACTIVE))
                .thenReturn(List.of(expired));
        when(seasonRepository.findByCode("WEEKLY_20260727")).thenReturn(Optional.empty());
        when(seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.FINALIZING))
                .thenReturn(List.of());
        when(seasonRepository.saveAndFlush(any(RankingSeason.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.rolloverWeeklySeasons(Instant.parse("2026-07-26T15:00:00Z"));

        assertThat(expired.getStatus()).isEqualTo(RankingSeasonStatus.FINALIZING);
        verify(seasonRepository).saveAndFlush(argThat(season ->
                "WEEKLY_20260727".equals(season.getCode())
                        && season.getStatus() == RankingSeasonStatus.ACTIVE
        ));
        verify(entryRepository, never()).snapshotFinalRanks(any(), any());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void rolloverAfterFinalizationDelayBackfillsSnapshotsRanksAndClosesFinalizingSeason() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        RankingSeason current = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 27),
                Instant.parse("2026-07-26T15:00:00Z"),
                Instant.parse("2026-08-02T15:00:00Z")
        );
        RankingSeason finalizing = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        ReflectionTestUtils.setField(finalizing, "id", 1L);
        finalizing.startFinalizing();
        when(seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.ACTIVE))
                .thenReturn(List.of(current));
        when(seasonRepository.findByCode("WEEKLY_20260727")).thenReturn(Optional.of(current));
        when(seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.FINALIZING))
                .thenReturn(List.of(finalizing));
        when(entryRepository.countFinalRankConflicts(1L)).thenReturn(0L);
        when(seasonRepository.findById(1L)).thenReturn(Optional.of(finalizing));

        service.rolloverWeeklySeasons(Instant.parse("2026-07-26T15:10:00Z"));

        verify(backfillService).backfillFinalizingWeeklySeason(
                finalizing,
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27)
        );
        verify(entryRepository).snapshotFinalRanks(1L, Instant.parse("2026-07-26T15:10:00Z"));
        assertThat(finalizing.getStatus()).isEqualTo(RankingSeasonStatus.CLOSED);
        assertThat(finalizing.getClosedAt()).isEqualTo(Instant.parse("2026-07-26T15:10:00Z"));
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void rolloverBeforeFinalizationDelayDoesNotCloseFinalizingSeason() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        RankingSeason current = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 27),
                Instant.parse("2026-07-26T15:00:00Z"),
                Instant.parse("2026-08-02T15:00:00Z")
        );
        RankingSeason finalizing = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        finalizing.startFinalizing();
        when(seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.ACTIVE))
                .thenReturn(List.of(current));
        when(seasonRepository.findByCode("WEEKLY_20260727")).thenReturn(Optional.of(current));
        when(seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.FINALIZING))
                .thenReturn(List.of(finalizing));

        service.rolloverWeeklySeasons(Instant.parse("2026-07-26T15:09:59Z"));

        assertThat(finalizing.getStatus()).isEqualTo(RankingSeasonStatus.FINALIZING);
        verify(backfillService, never()).backfillFinalizingWeeklySeason(any(), any(), any());
        verify(entryRepository, never()).snapshotFinalRanks(any(), any());
    }
}

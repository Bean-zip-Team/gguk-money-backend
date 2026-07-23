package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingType;
import com.ggukmoney.beanzip.domain.ranking.event.RankingWeeklySeasonActivatedEvent;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonLockRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RankingSeasonService {

    private final RankingSeasonRepository seasonRepository;
    private final RankingSeasonLockRepository seasonLockRepository;
    private final RankingEntryRepository entryRepository;
    private final RankingProperties properties;
    private final ObjectProvider<RankingBackfillService> backfillServiceProvider;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private final ZoneId businessZoneId;
    private final ApplicationEventPublisher eventPublisher;

    public Optional<RankingSeason> findActiveAllTimeSeason() {
        return seasonRepository.findByCodeAndStatus(RankingSeason.ALL_TIME_CODE, RankingSeasonStatus.ACTIVE);
    }

    public RankingSeason getActiveAllTimeSeason() {
        return findActiveAllTimeSeason()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RANKING_SEASON_NOT_FOUND"));
    }

    public Optional<RankingSeason> findActiveWeeklySeason() {
        return seasonRepository.findByRankingTypeAndStatus(RankingType.WEEKLY, RankingSeasonStatus.ACTIVE);
    }

    public RankingSeason getActiveWeeklySeason() {
        return findActiveWeeklySeason()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RANKING_SEASON_NOT_FOUND"));
    }

    public Optional<RankingSeason> findWeeklySeasonContaining(Instant occurredAt) {
        return seasonRepository.findByRankingTypeAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThan(
                RankingType.WEEKLY,
                RankingSeasonStatus.ACTIVE,
                occurredAt,
                occurredAt
        );
    }

    public RankingSeason getOrCreateActiveAllTimeSeason() {
        Optional<RankingSeason> existing = findActiveAllTimeSeason();
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return createActiveAllTimeSeasonInNewTransaction();
        } catch (DataIntegrityViolationException exception) {
            return findActiveAllTimeSeason().orElseThrow(() -> exception);
        }
    }

    public RankingSeason ensureCurrentWeeklySeason(Instant now) {
        try {
            return ensureCurrentWeeklySeasonInNewTransaction(now);
        } catch (DataIntegrityViolationException exception) {
            return findActiveWeeklySeason().orElseThrow(() -> exception);
        }
    }

    public void rolloverWeeklySeasons(Instant now) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            seasonLockRepository.acquireWeeklyRankingTransactionLock(properties.weeklyAdvisoryLockKey());
            WeeklyBoundary boundary = weeklyBoundary(now);
            finalizeExpiredActiveWeeklySeasons(now);
            ensureActiveWeeklySeason(boundary);
            closeEligibleFinalizingWeeklySeasons(now);
            transactionManager.commit(status);
        } catch (RuntimeException exception) {
            transactionManager.rollback(status);
            throw exception;
        }
    }

    private RankingSeason ensureCurrentWeeklySeasonInNewTransaction(Instant now) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            seasonLockRepository.acquireWeeklyRankingTransactionLock(properties.weeklyAdvisoryLockKey());
            WeeklyBoundary boundary = weeklyBoundary(now);
            finalizeExpiredActiveWeeklySeasons(now);
            RankingSeason season = ensureActiveWeeklySeason(boundary);
            transactionManager.commit(status);
            return season;
        } catch (RuntimeException exception) {
            transactionManager.rollback(status);
            throw exception;
        }
    }

    private void finalizeExpiredActiveWeeklySeasons(Instant now) {
        seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.ACTIVE)
                .stream()
                .filter(season -> season.getEndsAt() != null && !season.getEndsAt().isAfter(now))
                .sorted(Comparator.comparing(RankingSeason::getEndsAt))
                .forEach(RankingSeason::startFinalizing);
    }

    private RankingSeason ensureActiveWeeklySeason(WeeklyBoundary boundary) {
        RankingSeason season = seasonRepository.findByCode(RankingSeason.weeklyCode(boundary.weekStartDate()))
                .orElse(null);
        if (season != null) {
            return season;
        }
        RankingSeason created = seasonRepository.saveAndFlush(RankingSeason.activeWeekly(
                boundary.weekStartDate(),
                boundary.startsAt(),
                boundary.endsAt()
        ));
        eventPublisher.publishEvent(new RankingWeeklySeasonActivatedEvent(created.getId()));
        return created;
    }

    private void closeEligibleFinalizingWeeklySeasons(Instant now) {
        seasonRepository.findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType.WEEKLY, RankingSeasonStatus.FINALIZING)
                .stream()
                .filter(season -> season.getEndsAt() != null)
                .filter(season -> !season.getEndsAt().plus(properties.weeklyFinalizationDelay()).isAfter(now))
                .forEach(season -> closeFinalizingWeeklySeason(season, now));
    }

    private void closeFinalizingWeeklySeason(RankingSeason season, Instant closedAt) {
        LocalDate startDate = LocalDate.ofInstant(season.getStartsAt(), businessZoneId);
        LocalDate endDate = LocalDate.ofInstant(season.getEndsAt(), businessZoneId);
        backfillServiceProvider.getObject().backfillFinalizingWeeklySeason(season, startDate, endDate);
        long conflicts = entryRepository.countFinalRankConflicts(season.getId());
        if (conflicts > 0) {
            throw new IllegalStateException("final rank conflict detected seasonId=" + season.getId());
        }
        entryRepository.snapshotFinalRanks(season.getId(), closedAt);
        RankingSeason refreshed = seasonRepository.findById(season.getId())
                .orElseThrow(() -> new IllegalStateException("ranking season not found seasonId=" + season.getId()));
        refreshed.close(closedAt);
    }

    private RankingSeason createActiveAllTimeSeasonInNewTransaction() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            RankingSeason season = seasonRepository.saveAndFlush(RankingSeason.activeAllTime(clock.instant()));
            transactionManager.commit(status);
            return season;
        } catch (RuntimeException exception) {
            transactionManager.rollback(status);
            throw exception;
        }
    }

    private WeeklyBoundary weeklyBoundary(Instant now) {
        if (!properties.weeklyResetTime().equals(java.time.LocalTime.MIDNIGHT)) {
            throw new IllegalStateException("ranking.weekly.reset-time must be 00:00");
        }
        ZonedDateTime zonedNow = now.atZone(businessZoneId);
        LocalDate weekStartDate = zonedNow.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(properties.weeklyResetDayOfWeek()));
        Instant startsAt = weekStartDate.atStartOfDay(businessZoneId).toInstant();
        Instant endsAt = weekStartDate.plusWeeks(1).atStartOfDay(businessZoneId).toInstant();
        return new WeeklyBoundary(weekStartDate, startsAt, endsAt);
    }

    private record WeeklyBoundary(LocalDate weekStartDate, Instant startsAt, Instant endsAt) {
    }
}

package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingType;
import com.ggukmoney.beanzip.domain.ranking.event.RankingWeeklySeasonActivatedEvent;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonLockRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
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
    private final RankingProperties properties;
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

    private RankingSeason ensureCurrentWeeklySeasonInNewTransaction(Instant now) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            seasonLockRepository.acquireWeeklyRankingTransactionLock(properties.weeklyAdvisoryLockKey());
            WeeklyBoundary boundary = weeklyBoundary(now);
            finalizeExpiredActiveWeeklySeasons(now);
            RankingSeason season = seasonRepository.findByCode(RankingSeason.weeklyCode(boundary.weekStartDate()))
                    .orElse(null);
            boolean created = false;
            if (season == null) {
                season = seasonRepository.saveAndFlush(RankingSeason.activeWeekly(
                        boundary.weekStartDate(),
                        boundary.startsAt(),
                        boundary.endsAt()
                ));
                created = true;
            }
            if (created) {
                eventPublisher.publishEvent(new RankingWeeklySeasonActivatedEvent(season.getId()));
            }
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

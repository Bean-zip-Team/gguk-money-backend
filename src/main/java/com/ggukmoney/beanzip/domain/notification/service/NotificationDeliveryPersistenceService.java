package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationRankState;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationRankStateRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryPersistenceService {

    private static final String DEDUPE_CONSTRAINT_NAME = "uq_notification_delivery_dedupe_key";
    private static final int SIGNIFICANT_RANK_CHANGE = 3;
    private static final long TOP_TEN = 10L;

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRankStateRepository rankStateRepository;
    private final RankingSeasonService rankingSeasonService;
    private final RankingEntryRepository rankingEntryRepository;
    private final NotificationTemplateProperties templateProperties;
    private final Clock clock;

    @Transactional
    public Optional<NotificationDelivery> createPending(
            UUID userId,
            NotificationType type,
            String dedupeKey,
            String templateSetCode
    ) {
        return createPendingInternal(userId, type, dedupeKey, templateSetCode);
    }

    @Transactional
    public Optional<NotificationDelivery> prepareRankChange(UUID userId) {
        if (!isRankChangeSendable(userId)) {
            return Optional.empty();
        }

        Optional<CurrentRank> currentRank = currentRank(userId);
        if (currentRank.isEmpty()) {
            return Optional.empty();
        }

        CurrentRank rank = currentRank.get();
        Optional<NotificationRankState> existingState = rankStateRepository.findByUserIdAndSeasonId(userId, rank.season().getId());
        if (existingState.isEmpty()) {
            rankStateRepository.saveAndFlush(NotificationRankState.record(userId, rank.season().getId(), rank.rank(), clock.instant()));
            return Optional.empty();
        }

        NotificationRankState state = existingState.get();
        long previousRank = state.getBaselineRank();
        Optional<NotificationDelivery> pending = Optional.empty();
        if (shouldSend(previousRank, rank.rank())) {
            pending = createPendingInternal(
                    userId,
                    NotificationType.RANK_CHANGE,
                    "RANK_CHANGE:%d:%s:%d:%d".formatted(rank.season().getId(), userId, previousRank, rank.rank()),
                    templateProperties.templateSetCode(NotificationType.RANK_CHANGE)
            );
        }

        state.updateBaseline(rank.season().getId(), rank.rank(), clock.instant());
        rankStateRepository.saveAndFlush(state);
        return pending;
    }

    @Transactional(readOnly = true)
    public boolean isRankPromptEligible(UUID userId) {
        return currentRank(userId).isPresent();
    }

    @Transactional
    public void captureRankBaselineOnAgreement(UUID userId) {
        currentRank(userId).ifPresent(rank -> {
            NotificationRankState state = rankStateRepository.findByUserIdAndSeasonId(userId, rank.season().getId())
                    .orElseGet(() -> NotificationRankState.record(userId, rank.season().getId(), rank.rank(), clock.instant()));
            state.updateBaseline(rank.season().getId(), rank.rank(), clock.instant());
            rankStateRepository.saveAndFlush(state);
        });
    }

    @Transactional
    public NotificationDelivery markSent(Long deliveryId, String contentId, String providerResponseJson) {
        NotificationDelivery delivery = getById(deliveryId);
        delivery.markSent(contentId, providerResponseJson);
        return delivery;
    }

    @Transactional
    public NotificationDelivery markRetryWaiting(Long deliveryId, String failureCode, String failureReason, String providerResponseJson) {
        NotificationDelivery delivery = getById(deliveryId);
        delivery.markRetryWaiting(failureCode, failureReason, providerResponseJson);
        return delivery;
    }

    @Transactional
    public NotificationDelivery markFailed(Long deliveryId, String failureCode, String failureReason, String providerResponseJson) {
        NotificationDelivery delivery = getById(deliveryId);
        delivery.markFailed(failureCode, failureReason, providerResponseJson);
        return delivery;
    }

    private Optional<NotificationDelivery> createPendingInternal(
            UUID userId,
            NotificationType type,
            String dedupeKey,
            String templateSetCode
    ) {
        if (!StringUtils.hasText(templateSetCode)) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        try {
            int inserted = deliveryRepository.insertPendingIfAbsent(
                    UUID.randomUUID(),
                    userId,
                    type.name(),
                    dedupeKey,
                    templateSetCode,
                    now
            );
            if (inserted == 0) {
                return Optional.empty();
            }
            return deliveryRepository.findByDedupeKey(dedupeKey);
        } catch (DataIntegrityViolationException exception) {
            if (isDedupeConstraintViolation(exception)) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private boolean isRankChangeSendable(UUID userId) {
        return preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE)
                .map(NotificationPreference::isSendable)
                .orElse(false);
    }

    private Optional<CurrentRank> currentRank(UUID userId) {
        return rankingSeasonService.findActiveWeeklySeason()
                .flatMap(season -> rankingEntryRepository.findMyParticipant(season, userId)
                        .map(participant -> new CurrentRank(
                                season,
                                rankingEntryRepository.countParticipantsAhead(season, participant.score(), userId.toString()) + 1
                        )));
    }

    private boolean shouldSend(long previousRank, long currentRank) {
        long rankChange = previousRank - currentRank;
        return Math.abs(rankChange) >= SIGNIFICANT_RANK_CHANGE
                || (previousRank > TOP_TEN && currentRank <= TOP_TEN)
                || (previousRank <= TOP_TEN && currentRank > TOP_TEN);
    }

    private boolean isDedupeConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && DEDUPE_CONSTRAINT_NAME.equals(constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private NotificationDelivery getById(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("notification delivery not found id=" + deliveryId));
    }

    private record CurrentRank(RankingSeason season, long rank) {
    }
}

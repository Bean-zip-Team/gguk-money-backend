package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDeliveryStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationRankState;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationRankStateRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryPersistenceService {

    private static final String DEDUPE_CONSTRAINT_NAME = "uq_notification_delivery_dedupe_key";
    private static final long TOP_TEN = 10L;

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRankStateRepository rankStateRepository;
    private final RankingSeasonService rankingSeasonService;
    private final RankingEntryRepository rankingEntryRepository;
    private final KeycapBoxAccountRepository keycapBoxAccountRepository;
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig;
    private final NotificationTemplateProperties templateProperties;
    private final Clock clock;

    @Value("${app.smart-message.rank-change.minimum-difference:3}")
    private int minimumRankChange = 3;

    private Duration rankChangeCooldown = Duration.ofHours(6);

    @Value("${app.smart-message.rank-change.cooldown:6h}")
    void setRankChangeCooldown(Duration rankChangeCooldown) {
        if (rankChangeCooldown.isNegative()) {
            throw new IllegalStateException("app.smart-message.rank-change.cooldown must not be negative");
        }
        this.rankChangeCooldown = rankChangeCooldown;
    }

    @Transactional
    public Optional<NotificationDelivery> createPending(
            UUID userId,
            NotificationType type,
            String dedupeKey,
            String templateSetCode
    ) {
        return createPendingInternal(userId, type, dedupeKey, templateSetCode, "{}");
    }

    @Transactional
    public Optional<NotificationDelivery> createPending(
            UUID userId, NotificationType type, String dedupeKey, String templateSetCode, String contextJson
    ) {
        return createPendingInternal(userId, type, dedupeKey, templateSetCode, contextJson);
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
        Instant now = clock.instant();
        Optional<NotificationDelivery> pending = Optional.empty();
        if (shouldSend(previousRank, rank.rank()) && !isRankChangeCooldownActive(userId, now)) {
            pending = createPendingInternal(
                    userId,
                    NotificationType.RANK_CHANGE,
                    "RANK_CHANGE:%d:%s:%d".formatted(
                            rank.season().getId(), userId, state.getBaselineRecordedAt().toEpochMilli()),
                    templateProperties.campaignCode(NotificationType.RANK_CHANGE),
                    "{\"currentRank\":%d,\"rankChange\":%d,\"direction\":\"%s\"}".formatted(
                            rank.rank(), Math.abs(previousRank - rank.rank()), previousRank > rank.rank() ? "UP" : "DOWN")
            );
        }

        state.updateBaseline(rank.season().getId(), rank.rank(), now);
        rankStateRepository.saveAndFlush(state);
        return pending;
    }

    @Transactional
    public Optional<NotificationDelivery> prepareKeycapBoxOpenAvailable(UUID userId, Instant now) {
        String campaignCode = templateProperties.campaignCode(NotificationType.KEYCAP_BOX_OPEN_AVAILABLE);
        if (!StringUtils.hasText(campaignCode) || !isKeycapBoxOpenAvailableSendable(userId)) {
            return Optional.empty();
        }

        Duration cycleDuration = keycapBoxPolicyConfig.openCycleDuration();
        int freeOpenLimit = keycapBoxPolicyConfig.freeOpenLimit();
        int adOpenLimit = keycapBoxPolicyConfig.adOpenLimit();
        if (freeOpenLimit == 0 && adOpenLimit == 0) {
            return Optional.empty();
        }

        Optional<KeycapBoxAccount> lockedAccount = keycapBoxAccountRepository.findByUserIdForUpdate(userId);
        if (lockedAccount.isEmpty()) {
            return Optional.empty();
        }

        KeycapBoxAccount account = lockedAccount.get();
        Instant cutoff = now.minus(cycleDuration);
        if (!account.hasBox()
                || account.getFreeOpenUsedCount() < freeOpenLimit
                || account.getAdOpenUsedCount() < adOpenLimit
                || account.getOpenCycleStartedAt().isAfter(cutoff)) {
            return Optional.empty();
        }

        Instant effectiveCycleStartedAt = account.calculateEffectiveOpenCycleStartedAt(now, cycleDuration);
        String dedupeKey = "KEYCAP_BOX_OPEN_AVAILABLE:%s:%s".formatted(userId, effectiveCycleStartedAt);
        Optional<NotificationDelivery> pending = createPendingInternal(
                userId,
                NotificationType.KEYCAP_BOX_OPEN_AVAILABLE,
                dedupeKey,
                campaignCode,
                "{}"
        );
        if (pending.isEmpty()) {
            return Optional.empty();
        }

        account.refreshOpenCycle(now, cycleDuration);
        keycapBoxAccountRepository.saveAndFlush(account);
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
            String templateSetCode,
            String contextJson
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
                    contextJson,
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

    private boolean isKeycapBoxOpenAvailableSendable(UUID userId) {
        return preferenceRepository.findByUserIdAndType(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE)
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
        long rankDrop = currentRank - previousRank;
        return rankDrop >= minimumRankChange
                || (previousRank <= TOP_TEN && currentRank > TOP_TEN);
    }

    private boolean isRankChangeCooldownActive(UUID userId, Instant now) {
        return !rankChangeCooldown.isZero()
                && deliveryRepository.existsByUserIdAndTypeAndStatusAndRequestedAtAfter(
                        userId,
                        NotificationType.RANK_CHANGE,
                        NotificationDeliveryStatus.SENT,
                        now.minus(rankChangeCooldown)
                );
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

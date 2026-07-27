package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.booster.repository.BoosterGrantRepository;
import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient;
import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.event.WeeklyRewardAvailableEvent;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private final NotificationDeliveryPersistenceService persistenceService;
    private final NotificationPreferenceRepository preferenceRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final BoosterGrantRepository boosterGrantRepository;
    private final UserTapDailyRepository userTapDailyRepository;
    private final TapPolicyConfig tapPolicyConfig;
    private final NotificationTemplateProperties templateProperties;
    private final TossSmartMessageClient smartMessageClient;

    public Optional<NotificationDelivery> handleWeeklyRewardAvailable(WeeklyRewardAvailableEvent event) {
        return send(
                event.userId(),
                NotificationType.WEEKLY_REWARD_AVAILABLE,
                "WEEKLY_REWARD_AVAILABLE:" + event.userId() + ":" + event.rewardCycleKey(),
                "{\"rewardCycleKey\":\"%s\",\"availableAt\":\"%s\"}".formatted(event.rewardCycleKey(), event.availableAt())
        );
    }

    public Optional<NotificationDelivery> evaluateRankChange(UUID userId) {
        return dispatch(persistenceService.prepareRankChange(userId));
    }

    public List<NotificationDelivery> sendBoosterRechargedForDate(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        int dailyLimit = tapPolicyConfig.boosterDailyLimit();
        List<NotificationDelivery> deliveries = new ArrayList<>();

        for (UUID userId : boosterGrantRepository.findUserIdsWhoExhaustedDailyBoosters(yesterday, dailyLimit)) {
            try {
                sendBoosterRechargedToUser(userId, today).ifPresent(deliveries::add);
            } catch (Exception exception) {
                log.error("Failed to send booster recharged notification. userId={}, date={}", userId, today, exception);
            }
        }
        return deliveries;
    }

    public List<NotificationDelivery> sendMorningNotifications(LocalDate today) {
        List<NotificationDelivery> deliveries = new ArrayList<>();
        List<UUID> rechargedCandidates = boosterGrantRepository.findUserIdsWhoExhaustedDailyBoosters(
                today.minusDays(1), tapPolicyConfig.boosterDailyLimit());
        for (UUID userId : rechargedCandidates) {
            try {
                sendBoosterRechargedToUser(userId, today).ifPresent(deliveries::add);
            } catch (Exception exception) {
                log.error("Failed to process morning booster recharge. userId={}, date={}", userId, today, exception);
            }
        }
        for (UUID userId : preferenceRepository.findSendableUserIdsByType(NotificationType.DAILY_REMINDER)) {
            if (rechargedCandidates.contains(userId)) {
                continue;
            }
            try {
                if (!hasTodayActivity(userId, today)) {
                    send(userId, NotificationType.DAILY_REMINDER,
                            "DAILY_REMINDER:" + userId + ":" + compactDate(today), "{}").ifPresent(deliveries::add);
                }
            } catch (Exception exception) {
                log.error("Failed to process morning reminder. userId={}, date={}", userId, today, exception);
            }
        }
        return deliveries;
    }

    public List<NotificationDelivery> sendEveningNotifications(LocalDate today) {
        List<NotificationDelivery> deliveries = new ArrayList<>();
        List<UUID> rankSelected = new ArrayList<>();
        for (UUID userId : preferenceRepository.findSendableUserIdsByType(NotificationType.RANK_CHANGE)) {
            try {
                evaluateRankChange(userId).ifPresent(delivery -> {
                    deliveries.add(delivery);
                    rankSelected.add(userId);
                });
            } catch (Exception exception) {
                log.error("Failed to process rank notification. userId={}, date={}", userId, today, exception);
            }
        }
        int dailyLimit = tapPolicyConfig.boosterDailyLimit();
        for (UUID userId : preferenceRepository.findSendableUserIdsByType(NotificationType.BOOSTER_UNUSED)) {
            if (rankSelected.contains(userId)) {
                continue;
            }
            try {
                if (!hasValidTapToday(userId, today)
                        && boosterGrantRepository.countByUserIdAndGrantDate(userId, today) < dailyLimit) {
                    send(userId, NotificationType.BOOSTER_UNUSED,
                            "BOOSTER_UNUSED:" + userId + ":" + compactDate(today), "{}").ifPresent(deliveries::add);
                }
            } catch (Exception exception) {
                log.error("Failed to process booster unused notification. userId={}, date={}", userId, today, exception);
            }
        }
        return deliveries;
    }

    private Optional<NotificationDelivery> sendBoosterRechargedToUser(UUID userId, LocalDate today) {
        if (boosterGrantRepository.countByUserIdAndGrantDate(userId, today) > 0) {
            return Optional.empty();
        }
        return send(
                userId,
                NotificationType.BOOSTER_RECHARGED,
                "BOOSTER_RECHARGED:" + userId + ":" + today.toString().replace("-", ""),
                "{}"
        );
    }

    private Optional<NotificationDelivery> send(UUID userId, NotificationType type, String dedupeKey, String contextJson) {
        if (!templateProperties.isConfigured(type) || !isSendable(userId, type)) {
            return Optional.empty();
        }
        return dispatch(persistenceService.createPending(userId, type, dedupeKey, templateProperties.campaignCode(type), contextJson));
    }

    private Optional<NotificationDelivery> dispatch(Optional<NotificationDelivery> pending) {
        if (pending.isEmpty()) {
            return Optional.empty();
        }

        NotificationDelivery delivery = pending.get();
        Optional<AuthIdentity> identity = authIdentityRepository.findByUserIdAndProvider(delivery.getUserId(), AuthIdentity.Provider.TOSS);
        if (identity.isEmpty()) {
            return Optional.of(persistenceService.markFailed(
                    delivery.getId(),
                    "TOSS_USER_KEY_MISSING",
                    "Toss auth identity was not found",
                    null
            ));
        }

        TossSmartMessageClient.SendResult result = smartMessageClient.sendMessage(
                identity.get().getProviderUserId(),
                delivery.getTemplateSetCode(),
                delivery.getContextJson()
        );
        if (result.succeeded()) {
            return Optional.of(persistenceService.markSent(delivery.getId(), result.contentId(), result.responseBody()));
        }
        if (result.retryable()) {
            return Optional.of(persistenceService.markRetryWaiting(
                    delivery.getId(), result.errorCode(), result.reason(), result.responseBody()
            ));
        }
        return Optional.of(persistenceService.markFailed(
                delivery.getId(), result.errorCode(), result.reason(), result.responseBody()
        ));
    }

    private boolean isSendable(UUID userId, NotificationType type) {
        return preferenceRepository.findByUserIdAndType(userId, type)
                .map(NotificationPreference::isSendable)
                .orElse(false);
    }

    private boolean hasTodayActivity(UUID userId, LocalDate today) {
        return hasValidTapToday(userId, today) || boosterGrantRepository.countByUserIdAndGrantDate(userId, today) > 0;
    }

    private boolean hasValidTapToday(UUID userId, LocalDate today) {
        return userTapDailyRepository.existsByUserIdAndTapDateAndValidTapCountGreaterThan(userId, today, 0);
    }

    private String compactDate(LocalDate date) {
        return date.toString().replace("-", "");
    }
}

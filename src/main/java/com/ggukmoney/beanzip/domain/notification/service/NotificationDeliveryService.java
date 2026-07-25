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
    private final TapPolicyConfig tapPolicyConfig;
    private final NotificationTemplateProperties templateProperties;
    private final TossSmartMessageClient smartMessageClient;

    public Optional<NotificationDelivery> handleWeeklyRewardAvailable(WeeklyRewardAvailableEvent event) {
        if (!isSendable(event.userId(), NotificationType.WEEKLY_REWARD_AVAILABLE)) {
            return Optional.empty();
        }
        return dispatch(persistenceService.createPending(
                event.userId(),
                NotificationType.WEEKLY_REWARD_AVAILABLE,
                "WEEKLY_REWARD_AVAILABLE:" + event.userId() + ":" + event.rewardCycleKey(),
                templateProperties.templateSetCode(NotificationType.WEEKLY_REWARD_AVAILABLE)
        ));
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

    private Optional<NotificationDelivery> sendBoosterRechargedToUser(UUID userId, LocalDate today) {
        if (boosterGrantRepository.countByUserIdAndGrantDate(userId, today) > 0
                || !isSendable(userId, NotificationType.BOOSTER_RECHARGED)) {
            return Optional.empty();
        }
        return dispatch(persistenceService.createPending(
                userId,
                NotificationType.BOOSTER_RECHARGED,
                "BOOSTER_RECHARGED:" + userId + ":" + today.toString().replace("-", ""),
                templateProperties.templateSetCode(NotificationType.BOOSTER_RECHARGED)
        ));
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
                delivery.getTemplateSetCode()
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
}

package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateNotificationPreferenceAgreementRequest;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceListResponse;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceResponse;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationTemplateProperties templateProperties;
    private final NotificationDeliveryPersistenceService persistenceService;

    public NotificationPreferenceListResponse list(UUID userId) {
        return new NotificationPreferenceListResponse(
                Arrays.stream(NotificationType.values())
                        .filter(templateProperties::isConfigured)
                        .map(type -> resolvePreference(userId, type))
                        .map(preference -> toResponse(preference, promptEligible(userId, preference.getType())))
                        .toList()
        );
    }

    @Transactional
    public NotificationPreferenceResponse agree(UUID userId, UpdateNotificationPreferenceAgreementRequest request) {
        try {
            NotificationPreference preference = preferenceFor(userId, request.type());
            preference.applyAgreement(request.agreementResult());
            if (request.type() == NotificationType.RANK_CHANGE
                    && preference.getAgreementStatus() == NotificationAgreementStatus.AGREED) {
                persistenceService.captureRankBaselineOnAgreement(userId);
            }
            return toResponse(preference, promptEligible(userId, request.type()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_NOTIFICATION_AGREEMENT", exception);
        }
    }

    @Transactional
    public NotificationPreferenceResponse updateEnabled(UUID userId, NotificationType type, boolean enabled) {
        NotificationPreference preference = preferenceFor(userId, type);
        if (enabled && preference.getAgreementStatus() != NotificationAgreementStatus.AGREED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "NOTIFICATION_AGREEMENT_REQUIRED");
        }
        preference.updateEnabled(enabled);
        return toResponse(preference, promptEligible(userId, type));
    }

    private NotificationPreference resolvePreference(UUID userId, NotificationType type) {
        return preferenceRepository.findByUserIdAndType(userId, type)
                .orElseGet(() -> NotificationPreference.defaultOf(userId, type));
    }

    private NotificationPreference preferenceFor(UUID userId, NotificationType type) {
        return preferenceRepository.findByUserIdAndType(userId, type)
                .orElseGet(() -> preferenceRepository.save(NotificationPreference.defaultOf(userId, type)));
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference preference, boolean promptEligible) {
        return new NotificationPreferenceResponse(
                preference.getType(),
                preference.isEnabled(),
                preference.getAgreementStatus(),
                templateProperties.campaignCode(preference.getType()),
                promptEligible
        );
    }

    private boolean promptEligible(UUID userId, NotificationType type) {
        return type == NotificationType.RANK_CHANGE && persistenceService.isRankPromptEligible(userId);
    }
}

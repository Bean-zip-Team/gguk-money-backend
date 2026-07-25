package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.dto.request.NotificationAgreementRequest;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceListResponse;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceResponse;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationTemplateProperties templateProperties;
    private final NotificationDeliveryPersistenceService persistenceService;

    public NotificationPreferenceListResponse list(UUID userId) {
        List<NotificationPreference> preferences = preferenceViewsFor(userId);
        return toListResponse(userId, preferences);
    }

    private NotificationPreferenceListResponse toListResponse(UUID userId, List<NotificationPreference> preferences) {
        return new NotificationPreferenceListResponse(
                templateProperties.agreementTemplateCode(),
                globalAgreementStatus(preferences),
                preferences.stream().allMatch(NotificationPreference::isEnabled),
                preferences.stream().map(preference -> toResponse(preference, promptEligible(userId, preference.getType()))).toList()
        );
    }

    @Transactional
    public NotificationPreferenceListResponse agree(UUID userId, NotificationAgreementRequest request) {
        try {
            List<NotificationPreference> preferences = preferencesFor(userId);
            preferences.forEach(preference -> preference.applyAgreement(request.agreementResult()));
            if (globalAgreementStatus(preferences) == NotificationAgreementStatus.AGREED) {
                persistenceService.captureRankBaselineOnAgreement(userId);
            }
            return toListResponse(userId, preferences);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_NOTIFICATION_AGREEMENT", exception);
        }
    }

    @Transactional
    public NotificationPreferenceListResponse updateGlobalEnabled(UUID userId, boolean enabled) {
        List<NotificationPreference> preferences = preferencesFor(userId);
        if (enabled && globalAgreementStatus(preferences) != NotificationAgreementStatus.AGREED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "NOTIFICATION_AGREEMENT_REQUIRED");
        }
        preferences.forEach(preference -> preference.updateEnabled(enabled));
        return toListResponse(userId, preferences);
    }

    private NotificationPreference resolvePreference(UUID userId, NotificationType type) {
        return preferenceRepository.findByUserIdAndType(userId, type)
                .orElseGet(() -> NotificationPreference.defaultOf(userId, type));
    }

    private List<NotificationPreference> preferencesFor(UUID userId) {
        return Arrays.stream(NotificationType.values())
                .map(type -> preferenceRepository.findByUserIdAndType(userId, type)
                        .orElseGet(() -> preferenceRepository.save(NotificationPreference.defaultOf(userId, type))))
                .toList();
    }

    private List<NotificationPreference> preferenceViewsFor(UUID userId) {
        return Arrays.stream(NotificationType.values())
                .map(type -> resolvePreference(userId, type))
                .toList();
    }

    private NotificationAgreementStatus globalAgreementStatus(List<NotificationPreference> preferences) {
        if (preferences.stream().allMatch(preference -> preference.getAgreementStatus() == NotificationAgreementStatus.AGREED)) {
            return NotificationAgreementStatus.AGREED;
        }
        if (preferences.stream().allMatch(preference -> preference.getAgreementStatus() == NotificationAgreementStatus.REJECTED)) {
            return NotificationAgreementStatus.REJECTED;
        }
        return NotificationAgreementStatus.NOT_REQUESTED;
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference preference, boolean promptEligible) {
        return new NotificationPreferenceResponse(
                preference.getType(),
                preference.isEnabled(),
                preference.getAgreementStatus(),
                templateProperties.templateCode(preference.getType()),
                promptEligible
        );
    }

    private boolean promptEligible(UUID userId, NotificationType type) {
        return type == NotificationType.RANK_CHANGE && persistenceService.isRankPromptEligible(userId);
    }
}

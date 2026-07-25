package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceListResponse;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceResponse;
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
        return new NotificationPreferenceListResponse(Arrays.stream(NotificationType.values())
                .map(type -> toResponse(resolvePreference(userId, type), promptEligible(userId, type)))
                .toList());
    }

    @Transactional
    public NotificationPreferenceResponse update(UUID userId, UpdateNotificationPreferenceRequest request) {
        try {
            NotificationPreference preference = preferenceRepository.findByUserIdAndType(userId, request.type())
                    .orElseGet(() -> preferenceRepository.save(NotificationPreference.defaultOf(userId, request.type())));
            preference.applyAgreement(request.agreementResult());
            if (request.type() == NotificationType.RANK_CHANGE && preference.isSendable()) {
                persistenceService.captureRankBaselineOnAgreement(userId);
            }
            return toResponse(preference, promptEligible(userId, request.type()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_NOTIFICATION_AGREEMENT", exception);
        }
    }

    private NotificationPreference resolvePreference(UUID userId, NotificationType type) {
        return preferenceRepository.findByUserIdAndType(userId, type)
                .orElseGet(() -> NotificationPreference.defaultOf(userId, type));
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

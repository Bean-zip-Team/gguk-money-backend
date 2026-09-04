package com.ggukmoney.beanzip.domain.notification.dto.request;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceAgreementRequest(
        @NotNull NotificationType type,
        @NotBlank String agreementResult
) {
}

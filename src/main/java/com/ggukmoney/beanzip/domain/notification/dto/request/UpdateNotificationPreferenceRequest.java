package com.ggukmoney.beanzip.domain.notification.dto.request;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(
        @NotNull
        NotificationType type,
        boolean enabled,
        @NotBlank
        String agreementResult
) {
}

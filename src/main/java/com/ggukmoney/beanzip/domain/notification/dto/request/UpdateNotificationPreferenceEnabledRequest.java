package com.ggukmoney.beanzip.domain.notification.dto.request;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceEnabledRequest(
        @NotNull NotificationType type,
        boolean enabled
) {
}

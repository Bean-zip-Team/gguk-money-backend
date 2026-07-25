package com.ggukmoney.beanzip.domain.notification.dto.response;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;

public record NotificationPreferenceResponse(
        NotificationType type,
        boolean enabled,
        NotificationAgreementStatus agreementStatus,
        String templateCode,
        boolean promptEligible
) {
}

package com.ggukmoney.beanzip.domain.notification.dto.response;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;

import java.util.List;

public record NotificationPreferenceListResponse(
        String agreementTemplateCode,
        NotificationAgreementStatus agreementStatus,
        boolean enabled,
        List<NotificationPreferenceResponse> items
) {
}

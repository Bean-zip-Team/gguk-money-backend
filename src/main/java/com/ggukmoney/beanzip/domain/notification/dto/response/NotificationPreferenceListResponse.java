package com.ggukmoney.beanzip.domain.notification.dto.response;

import java.util.List;

public record NotificationPreferenceListResponse(
        List<NotificationPreferenceResponse> items
) {
}

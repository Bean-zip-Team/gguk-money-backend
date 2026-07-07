package com.ggukmoney.beanzip.domain.auth.dto.response;

public record TossUnlinkWebhookResponse(
        boolean processed,
        String referrer
) {
}

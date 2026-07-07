package com.ggukmoney.beanzip.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TossUnlinkWebhookRequest(
        @NotBlank String userKey,
        @NotBlank String referrer
) {
}

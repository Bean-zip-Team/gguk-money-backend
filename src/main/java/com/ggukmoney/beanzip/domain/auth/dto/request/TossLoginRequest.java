package com.ggukmoney.beanzip.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record TossLoginRequest(
        @NotBlank(message = "Toss authorizationCode is required.")
        String authorizationCode,
        String referrer,
        UUID onboardingAttemptId
) {
    public TossLoginRequest(String authorizationCode, String referrer) {
        this(authorizationCode, referrer, null);
    }
}

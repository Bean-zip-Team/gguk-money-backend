package com.ggukmoney.beanzip.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TossLoginRequest(
        @NotBlank(message = "Toss authorizationCode가 필요합니다.")
        String authorizationCode,
        String referrer
) {
}

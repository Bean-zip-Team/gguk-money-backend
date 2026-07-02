package com.ggukmoney.beanzip.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "리프레시 토큰이 필요합니다.")
        String refreshToken
) {
}

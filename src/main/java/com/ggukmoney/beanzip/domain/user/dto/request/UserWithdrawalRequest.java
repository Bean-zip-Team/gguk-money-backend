package com.ggukmoney.beanzip.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UserWithdrawalRequest(
        @NotBlank String authorizationCode,
        String referrer
) {
}

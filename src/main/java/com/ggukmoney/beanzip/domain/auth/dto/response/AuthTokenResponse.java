package com.ggukmoney.beanzip.domain.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AuthTokenResponse(
        UUID userId,
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        boolean newUser
) {
}

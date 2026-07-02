package com.ggukmoney.beanzip.domain.auth.dto.response;

import java.time.Instant;

public record AuthTokenResponse(
        String userPublicId,
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        boolean newUser
) {
}

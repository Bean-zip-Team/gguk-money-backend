package com.ggukmoney.beanzip.domain.auth.model;

import java.time.Instant;
import java.util.UUID;

public record AuthSession(
        UUID sessionId,
        UUID userId,
        String devicePublicId,
        String currentRefreshJtiHash,
        String refreshTokenHash,
        String tokenFamilyIdHash,
        String previousRefreshJtiHash,
        Instant rotatedAt,
        Instant issuedAt,
        Instant expiresAt,
        String status
) {
}

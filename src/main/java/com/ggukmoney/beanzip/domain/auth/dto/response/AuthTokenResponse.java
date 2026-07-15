package com.ggukmoney.beanzip.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "인증 토큰 응답")
public record AuthTokenResponse(
        @Schema(description = "사용자 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "액세스 토큰", example = "eyJhbGciOi...")
        String accessToken,
        @Schema(description = "리프레시 토큰", example = "refresh-token")
        String refreshToken,
        @Schema(description = "토큰 타입", example = "Bearer")
        String tokenType,
        @Schema(description = "액세스 토큰 만료 시각", example = "2026-07-15T01:30:00Z")
        Instant accessTokenExpiresAt,
        @Schema(description = "리프레시 토큰 만료 시각", example = "2026-08-14T01:00:00Z")
        Instant refreshTokenExpiresAt,
        @Schema(description = "신규 가입 여부", example = "true")
        boolean newUser,
        @Schema(description = "온보딩 보상 적용 여부", example = "false")
        boolean onboardingRewardApplied
) {
    public AuthTokenResponse(
            UUID userId,
            String accessToken,
            String refreshToken,
            String tokenType,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt,
            boolean newUser
    ) {
        this(userId, accessToken, refreshToken, tokenType, accessTokenExpiresAt, refreshTokenExpiresAt, newUser, false);
    }
}

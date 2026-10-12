package com.ggukmoney.beanzip.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "인증 토큰 응답이에요.")
public record AuthTokenResponse(
        @Schema(description = "사용자 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "API를 호출할 때 쓰는 액세스 토큰이에요.", example = "eyJhbGciOi...")
        String accessToken,
        @Schema(description = "토큰을 갱신할 때 쓰는 리프레시 토큰이에요.", example = "refresh-token")
        String refreshToken,
        @Schema(description = "토큰 타입이에요. 항상 `Bearer`예요.", example = "Bearer")
        String tokenType,
        @Schema(description = "액세스 토큰 만료 시각이에요.", example = "2026-07-15T01:30:00Z")
        Instant accessTokenExpiresAt,
        @Schema(description = "리프레시 토큰 만료 시각이에요.", example = "2026-08-14T01:00:00Z")
        Instant refreshTokenExpiresAt,
        @Schema(description = "신규 가입 여부예요. 처음 보는 Toss 계정으로 회원을 새로 만들었을 때만 `true`예요. 탈퇴했다가 다시 로그인한 회원과 토큰 갱신 응답은 `false`예요.", example = "true")
        boolean newUser,
        @Schema(description = "온보딩 보상 적용 여부예요. 토큰 갱신 응답에서는 항상 `false`예요.", example = "false")
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

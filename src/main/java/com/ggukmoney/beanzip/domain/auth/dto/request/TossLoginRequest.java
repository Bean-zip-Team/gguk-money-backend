package com.ggukmoney.beanzip.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

@Schema(description = "Toss 로그인 요청")
public record TossLoginRequest(
        @Schema(description = "Toss 인가 코드", example = "toss-authorization-code")
        @NotBlank(message = "Toss authorizationCode is required.")
        String authorizationCode,
        @Schema(description = "Toss 유입 referrer", example = "beanzip")
        String referrer,
        @Schema(description = "온보딩 보상 시도 ID. 온보딩 보상 수령 시 전달합니다.", example = "11111111-1111-1111-1111-111111111111")
        UUID onboardingAttemptId
) {
    public TossLoginRequest(String authorizationCode, String referrer) {
        this(authorizationCode, referrer, null);
    }
}

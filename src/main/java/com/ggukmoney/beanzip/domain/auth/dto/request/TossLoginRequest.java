package com.ggukmoney.beanzip.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

@Schema(description = "Toss 로그인 요청이에요.")
public record TossLoginRequest(
        @Schema(description = "Toss 로그인으로 받은 인가 코드예요.", example = "toss-authorization-code")
        @NotBlank(message = "Toss authorizationCode is required.")
        String authorizationCode,
        @Schema(description = "Toss 유입 경로(referrer) 값이에요. Toss 토큰을 발급할 때 함께 보내요.", example = "beanzip")
        String referrer,
        @Schema(description = "온보딩 보상 시도 ID예요. 온보딩 보상을 받을 때만 보내요.", example = "11111111-1111-1111-1111-111111111111")
        UUID onboardingAttemptId
) {
    public TossLoginRequest(String authorizationCode, String referrer) {
        this(authorizationCode, referrer, null);
    }
}

package com.ggukmoney.beanzip.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Toss 로그인 요청이에요.")
public record TossLoginRequest(
        @Schema(description = "Toss 로그인으로 받은 인가 코드예요.", example = "toss-authorization-code")
        @NotBlank(message = "Toss authorizationCode is required.")
        String authorizationCode,
        @Schema(description = "`appLogin()` 응답의 referrer 예요. 토스 앱이면 `DEFAULT`, 샌드박스 앱이면 `SANDBOX` 이고 유입 경로가 아니에요. Toss 토큰을 발급할 때 그대로 넘겨요.", example = "DEFAULT")
        String referrer,
        @Schema(description = "온보딩 보상 시도 ID예요. 온보딩 보상을 받을 때만 보내요.", example = "11111111-1111-1111-1111-111111111111")
        UUID onboardingAttemptId,
        @Schema(description = "미니앱이 열릴 때 토스가 넘겨 준 유입 경로(referrer 파라미터)예요. 예: `search`, `push`, `tossads`. 가입할 때 한 번만 저장하고 이후 로그인이나 탈퇴 후 재가입에서는 바꾸지 않아요. 없으면 보내지 않아도 돼요.", example = "search")
        @Size(max = 100, message = "signupReferrer는 100자 이하여야 합니다.")
        String signupReferrer
) {
    public TossLoginRequest(String authorizationCode, String referrer) {
        this(authorizationCode, referrer, null, null);
    }

    public TossLoginRequest(String authorizationCode, String referrer, UUID onboardingAttemptId) {
        this(authorizationCode, referrer, onboardingAttemptId, null);
    }
}

package com.ggukmoney.beanzip.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원 탈퇴 요청이에요.")
public record UserWithdrawalRequest(
        @Schema(description = "본인 확인과 Toss 연결 해제에 쓸 Toss 인가 코드예요.", example = "toss-authorization-code")
        @NotBlank String authorizationCode,
        @Schema(description = "`appLogin()` 응답의 referrer 예요. 토스 앱이면 `DEFAULT`, 샌드박스 앱이면 `SANDBOX` 예요. Toss 토큰을 발급할 때 그대로 넘겨요.", example = "DEFAULT")
        String referrer
) {
}

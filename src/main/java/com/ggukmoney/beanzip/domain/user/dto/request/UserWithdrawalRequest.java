package com.ggukmoney.beanzip.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원 탈퇴 요청이에요.")
public record UserWithdrawalRequest(
        @Schema(description = "본인 확인과 Toss 연결 해제에 쓸 Toss 인가 코드예요.", example = "toss-authorization-code")
        @NotBlank String authorizationCode,
        @Schema(description = "Toss 유입 경로(referrer) 값이에요. Toss 토큰을 발급할 때 함께 보내요.", example = "beanzip")
        String referrer
) {
}

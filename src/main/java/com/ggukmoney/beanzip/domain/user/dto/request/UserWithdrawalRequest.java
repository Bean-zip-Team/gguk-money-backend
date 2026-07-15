package com.ggukmoney.beanzip.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원 탈퇴 요청")
public record UserWithdrawalRequest(
        @Schema(description = "Toss 인가 코드", example = "toss-authorization-code")
        @NotBlank String authorizationCode,
        @Schema(description = "Toss 유입 referrer", example = "beanzip")
        String referrer
) {
}

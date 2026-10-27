package com.ggukmoney.beanzip.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 탈퇴 응답이에요.")
public record UserWithdrawalResponse(
        @Schema(description = "탈퇴 처리 여부예요. 성공하면 항상 `true`예요.", example = "true")
        boolean withdrawn
) {
}

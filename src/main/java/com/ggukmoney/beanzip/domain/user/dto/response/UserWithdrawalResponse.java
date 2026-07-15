package com.ggukmoney.beanzip.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 탈퇴 응답")
public record UserWithdrawalResponse(
        @Schema(description = "탈퇴 처리 여부", example = "true")
        boolean withdrawn
) {
}

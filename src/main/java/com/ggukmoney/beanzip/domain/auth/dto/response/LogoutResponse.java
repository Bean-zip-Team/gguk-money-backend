package com.ggukmoney.beanzip.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 세션 로그아웃 응답")
public record LogoutResponse(
        @Schema(description = "로그아웃 처리 여부", example = "true")
        boolean loggedOut
) {
}

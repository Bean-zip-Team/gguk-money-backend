package com.ggukmoney.beanzip.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전체 세션 로그아웃 응답")
public record LogoutAllResponse(
        @Schema(description = "전체 로그아웃 처리 여부", example = "true")
        boolean loggedOutAll,
        @Schema(description = "폐기된 세션 수", example = "3")
        long revokedSessionCount
) {
}

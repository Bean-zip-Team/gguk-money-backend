package com.ggukmoney.beanzip.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그아웃 요청")
public record LogoutRequest(
        @Schema(description = "현재 세션의 리프레시 토큰. 전달 시 리프레시 토큰까지 폐기합니다.", example = "refresh-token")
        String refreshToken
) {
}

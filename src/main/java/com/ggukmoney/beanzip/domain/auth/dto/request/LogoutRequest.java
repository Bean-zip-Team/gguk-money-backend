package com.ggukmoney.beanzip.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그아웃 요청이에요. 본문은 생략해도 돼요.")
public record LogoutRequest(
        @Schema(description = "현재 세션의 리프레시 토큰이에요. 보내면 현재 세션의 토큰과 일치하는지 확인하고, 다르면 401을 돌려줘요. 로그아웃하면 리프레시 토큰까지 폐기해요.", example = "refresh-token")
        String refreshToken
) {
}

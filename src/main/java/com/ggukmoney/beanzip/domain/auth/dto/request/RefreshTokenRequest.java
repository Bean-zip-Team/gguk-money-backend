package com.ggukmoney.beanzip.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청이에요.")
public record RefreshTokenRequest(
        @Schema(description = "로그인이나 직전 갱신에서 받은 리프레시 토큰이에요.", example = "refresh-token")
        @NotBlank(message = "리프레시 토큰이 필요합니다.")
        String refreshToken
) {
}

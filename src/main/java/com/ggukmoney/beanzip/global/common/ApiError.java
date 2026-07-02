package com.ggukmoney.beanzip.global.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 에러")
public record ApiError(
        @Schema(description = "에러 코드", example = "AUTH_REQUIRED")
        String code,
        @Schema(description = "에러 메시지", example = "인증이 필요합니다.")
        String message
) {
}

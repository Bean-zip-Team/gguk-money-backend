package com.ggukmoney.beanzip.global.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 오류 정보예요.")
public record ApiError(
        @Schema(description = "오류 코드예요.", example = "AUTH_REQUIRED")
        String code,
        @Schema(description = "오류 메시지예요.", example = "인증이 필요합니다.")
        String message
) {
}

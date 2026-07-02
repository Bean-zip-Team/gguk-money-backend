package com.ggukmoney.beanzip.global.common;

import com.ggukmoney.beanzip.global.logging.RequestLogContext;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 에러 응답")
public record ApiErrorResponse(
        @Schema(description = "요청 성공 여부", example = "false")
        boolean success,
        @Schema(description = "에러 정보")
        ApiError error,
        @Schema(description = "요청 추적 ID", example = "01J...")
        String traceId
) {

    public static ApiErrorResponse failure(String code, String message) {
        return new ApiErrorResponse(false, new ApiError(code, message), RequestLogContext.currentTraceIdOrDefault());
    }
}

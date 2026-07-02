package com.ggukmoney.beanzip.global.common;

import com.ggukmoney.beanzip.global.logging.RequestLogContext;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 응답")
public record ApiResponse<T>(
        @Schema(description = "요청 성공 여부", example = "true")
        boolean success,
        @Schema(description = "응답 데이터")
        T data,
        @Schema(description = "요청 추적 ID", example = "01J...")
        String traceId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, RequestLogContext.currentTraceIdOrDefault());
    }
}

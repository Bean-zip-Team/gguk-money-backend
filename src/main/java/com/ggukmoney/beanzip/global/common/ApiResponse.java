package com.ggukmoney.beanzip.global.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 응답이에요.")
public record ApiResponse<T>(
        @Schema(description = "요청 성공 여부예요. 성공 응답에서는 항상 true예요.", example = "true")
        boolean success,
        @Schema(description = "응답 데이터예요.")
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data);
    }
}

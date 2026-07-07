package com.ggukmoney.beanzip.global.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Common API response")
public record ApiResponse<T>(
        @Schema(description = "Request success", example = "true")
        boolean success,
        @Schema(description = "Response data")
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data);
    }
}

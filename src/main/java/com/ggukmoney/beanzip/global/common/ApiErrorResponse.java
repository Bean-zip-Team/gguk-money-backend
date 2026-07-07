package com.ggukmoney.beanzip.global.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Common API error response")
public record ApiErrorResponse(
        @Schema(description = "Request success", example = "false")
        boolean success,
        @Schema(description = "Error")
        ApiError error
) {

    public static ApiErrorResponse failure(String code, String message) {
        return new ApiErrorResponse(false, new ApiError(code, message));
    }
}

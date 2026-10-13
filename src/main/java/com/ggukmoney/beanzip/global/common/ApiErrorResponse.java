package com.ggukmoney.beanzip.global.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 오류 응답이에요.")
public record ApiErrorResponse(
        @Schema(description = "요청 성공 여부예요. 오류 응답에서는 항상 false예요.", example = "false")
        boolean success,
        @Schema(description = "오류 정보예요.")
        ApiError error
) {

    public static ApiErrorResponse failure(String code, String message) {
        return new ApiErrorResponse(false, new ApiError(code, message));
    }
}

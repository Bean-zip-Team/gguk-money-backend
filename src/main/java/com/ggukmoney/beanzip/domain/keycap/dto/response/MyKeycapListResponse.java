package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 키캡 목록 응답이에요.")
public record MyKeycapListResponse(
        @Schema(description = "내가 보유한 키캡 목록이에요.")
        List<MyKeycapItemResponse> keycaps
) {
}

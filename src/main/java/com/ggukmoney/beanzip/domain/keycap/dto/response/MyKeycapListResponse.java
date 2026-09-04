package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 키캡 목록 응답")
public record MyKeycapListResponse(
        @Schema(description = "내 보유 키캡 목록")
        List<MyKeycapItemResponse> keycaps
) {
}

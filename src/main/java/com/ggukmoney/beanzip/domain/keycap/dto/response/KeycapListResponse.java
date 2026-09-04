package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "키캡 카탈로그 목록 응답")
public record KeycapListResponse(
        @Schema(description = "키캡 카탈로그 항목 목록")
        List<KeycapItemResponse> keycaps
) {
}

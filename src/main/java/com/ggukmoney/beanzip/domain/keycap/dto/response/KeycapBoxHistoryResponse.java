package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "키캡 상자 개봉 이력 페이지 응답")
public record KeycapBoxHistoryResponse(
        @Schema(description = "개봉 이력 목록")
        List<KeycapBoxHistoryItemResponse> content,
        @Schema(description = "다음 페이지 커서", example = "MjAyNi0wNy0xNVQwMTowMDowMFo6MTIz")
        String nextCursor,
        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext
) {
}

package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "키캡 상자 개봉 이력 페이지 응답이에요.")
public record KeycapBoxHistoryResponse(
        @Schema(description = "개봉 이력 목록이에요. 최근 개봉 순으로 정렬돼요.")
        List<KeycapBoxHistoryItemResponse> content,
        @Schema(description = "다음 페이지 커서예요. 다음 페이지가 없으면 null이에요.", example = "MjAyNi0wNy0xNVQwMTowMDowMFo6MTIz")
        String nextCursor,
        @Schema(description = "다음 페이지가 있는지 여부예요.", example = "true")
        boolean hasNext
) {
}

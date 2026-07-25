package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "주간 랭킹 히스토리 페이지 응답")
public record RankingHistoryResponse(
        @Schema(description = "종료된 주간 랭킹 이력 목록")
        List<RankingHistoryItemResponse> content,
        @Schema(description = "다음 페이지 조회에 전달할 커서입니다. 다음 페이지가 없으면 null입니다.", example = "MjAyNi0wNy0yNlQxNTowMDowMFp8MTIz")
        String nextCursor,
        @Schema(description = "다음 페이지 존재 여부입니다.", example = "true")
        boolean hasNext
) {
}

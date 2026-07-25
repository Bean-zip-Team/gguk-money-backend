package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Ranking history page response")
public record RankingHistoryResponse(
        @Schema(description = "Ranking history items")
        List<RankingHistoryItemResponse> content,
        @Schema(description = "Next page cursor", example = "MjAyNi0wNy0yNlQxNTowMDowMFp8MTIz")
        String nextCursor,
        @Schema(description = "Whether next page exists", example = "true")
        boolean hasNext
) {
}

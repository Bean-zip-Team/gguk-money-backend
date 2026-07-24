package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Ranking history item")
public record RankingHistoryItemResponse(
        @Schema(description = "Weekly season code", example = "WEEKLY_20260720")
        String seasonCode,
        @Schema(description = "Season start instant", example = "2026-07-19T15:00:00Z")
        Instant startedAt,
        @Schema(description = "Season end instant", example = "2026-07-26T15:00:00Z")
        Instant endsAt,
        @Schema(description = "My final rank in the closed weekly season", example = "7")
        Long myFinalRank,
        @Schema(description = "My final score in the closed weekly season", example = "950")
        long myFinalScore
) {
}

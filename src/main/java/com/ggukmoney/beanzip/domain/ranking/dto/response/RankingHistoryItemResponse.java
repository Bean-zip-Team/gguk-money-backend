package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "주간 랭킹 히스토리 항목")
public record RankingHistoryItemResponse(
        @Schema(description = "주간 시즌 코드", example = "WEEKLY_20260720")
        String seasonCode,
        @Schema(description = "시즌 시작 시각", example = "2026-07-19T15:00:00Z")
        Instant startedAt,
        @Schema(description = "시즌 종료 시각", example = "2026-07-26T15:00:00Z")
        Instant endsAt,
        @Schema(description = "해당 주간 시즌에서 확정된 내 순위", example = "7")
        Long myFinalRank,
        @Schema(description = "해당 주간 시즌에서 확정된 내 랭킹 점수", example = "950")
        long myFinalScore
) {
}

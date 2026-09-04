package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "현재 랭킹 시즌 정보")
public record RankingSeasonResponse(
        @Schema(description = "시즌 시작 시각", example = "2026-07-19T15:00:00Z")
        Instant startedAt,
        @Schema(description = "시즌 종료 시각", example = "2026-07-26T15:00:00Z")
        Instant endsAt,
        @Schema(description = "다음 리셋 예정 시각", example = "2026-07-26T15:00:00Z")
        Instant nextResetAt,
        @Schema(description = "리셋 요일", example = "MONDAY")
        String resetDayOfWeek,
        @Schema(description = "리셋 시각", example = "00:00")
        String resetTime,
        @Schema(description = "비즈니스 시간대", example = "Asia/Seoul")
        String timeZone
) {
}

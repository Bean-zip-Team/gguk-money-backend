package com.ggukmoney.beanzip.domain.booster.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "현재 부스터 상태 응답")
public record BoosterStatusResponse(
        @Schema(description = "현재 활성화 여부", example = "true")
        boolean active,
        @Schema(description = "적립 배율", example = "2.0")
        BigDecimal multiplier,
        @Schema(description = "남은 활성 시간(초)", example = "240")
        long remainingSeconds,
        @Schema(description = "부스터 종료 시각", example = "2026-07-15T01:05:00Z")
        Instant endsAt,
        @Schema(description = "오늘 남은 부스터 활성화 가능 횟수", example = "2")
        int remainingDailyCount
) {
}

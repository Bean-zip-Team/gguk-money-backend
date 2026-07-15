package com.ggukmoney.beanzip.domain.booster.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "부스터 활성화 응답")
public record BoosterActivateResponse(
        @Schema(description = "부스터 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID boosterId,
        @Schema(description = "활성화 여부", example = "true")
        boolean active,
        @Schema(description = "적립 배율", example = "2.0")
        BigDecimal multiplier,
        @Schema(description = "부스터 시작 시각", example = "2026-07-15T01:00:00Z")
        Instant startsAt,
        @Schema(description = "부스터 종료 시각", example = "2026-07-15T01:05:00Z")
        Instant endsAt,
        @Schema(description = "오늘 남은 부스터 활성화 가능 횟수", example = "2")
        int remainingDailyCount
) {
}

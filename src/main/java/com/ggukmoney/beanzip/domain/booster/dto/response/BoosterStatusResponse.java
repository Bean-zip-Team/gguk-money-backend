package com.ggukmoney.beanzip.domain.booster.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "현재 부스터 상태 응답이에요.")
public record BoosterStatusResponse(
        @Schema(description = "현재 활성화 여부예요. 켜진 부스터가 없거나 이미 끝났으면 false예요.", example = "true")
        boolean active,
        @Schema(description = "적립 배율이에요. 부스터가 꺼져 있으면 1이에요.", example = "2.0")
        BigDecimal multiplier,
        @Schema(description = "부스터가 끝날 때까지 남은 시간이에요. 단위는 초예요. 부스터가 꺼져 있으면 0이에요.", example = "240")
        long remainingSeconds,
        @Schema(description = "부스터 종료 시각이에요. 부스터가 꺼져 있으면 null이에요.", example = "2026-07-15T01:05:00Z", nullable = true)
        Instant endsAt,
        @Schema(description = "남은 부스터 활성화 가능 횟수예요. 일일 한도에서 최근 `tap.booster.limitWindowSeconds` 설정 기간(기본 86400초) 동안 시작한 부스터 수를 뺀 값이에요. 달력 날짜 기준이 아니에요. 0보다 작아지지 않아요.", example = "2")
        int remainingDailyCount
) {
}

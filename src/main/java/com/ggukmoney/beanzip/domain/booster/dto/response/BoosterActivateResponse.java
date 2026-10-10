package com.ggukmoney.beanzip.domain.booster.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "부스터 활성화 응답이에요.")
public record BoosterActivateResponse(
        @Schema(description = "부스터 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID boosterId,
        @Schema(description = "활성화 여부예요. 활성화에 성공하면 항상 true예요.", example = "true")
        boolean active,
        @Schema(description = "적립 배율이에요. 새로 활성화한 부스터는 2.0이에요.", example = "2.0")
        BigDecimal multiplier,
        @Schema(description = "부스터 시작 시각이에요.", example = "2026-07-15T01:00:00Z")
        Instant startsAt,
        @Schema(description = "부스터 종료 시각이에요. 시작 시각에 `tap.booster.durationSeconds` 설정 시간(기본 300초)을 더한 값이에요.", example = "2026-07-15T01:05:00Z")
        Instant endsAt,
        @Schema(description = "남은 부스터 활성화 가능 횟수예요. 일일 한도에서 최근 `tap.booster.limitWindowSeconds` 설정 기간(기본 86400초) 동안 시작한 부스터 수를 뺀 값이에요. 방금 활성화한 부스터도 포함해서 세요. 달력 날짜 기준이 아니에요.", example = "2")
        int remainingDailyCount
) {
}

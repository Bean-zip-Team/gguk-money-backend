package com.ggukmoney.beanzip.domain.tap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "오늘 탭 상태 응답이에요.")
public record TapTodayStatusResponse(
        @Schema(description = "기준 일자예요. 서비스 기준 시간대(기본 Asia/Seoul)로 정해요.", example = "2026-07-15")
        LocalDate date,
        @Schema(description = "오늘 직접 탭으로 쌓인 점수예요. 부스터 배율을 포함하고 클릭 크리티컬과 자동 클릭은 빼요. 보상 상한(3000탭)과 무관하게 계속 늘어나요.", example = "3763")
        int validTapCount,
        @Schema(description = "오늘 지급된 포인트예요.", example = "12")
        int pointEarnedToday,
        @Schema(description = "다음 포인트까지 남은 탭 수예요. 0이면 오늘은 더 이상 포인트가 지급되지 않아요. 포인트 상한이나 일일 탭 상한에 도달한 경우예요. 상한에 닿기 전에는 항상 1 이상이에요.", example = "10")
        int remainingTapsToNextPoint,
        @Schema(description = "다음 조각까지 남은 탭 수예요. 이름은 상자 시절 그대로지만 키캡 조각 진행도를 담는 현역 필드예요. `nextBoxRequiredTapCount` 필드 값에서 `boxProgressTapCount` 필드 값을 뺀 값이에요.", example = "80")
        int remainingTapsToNextBox,
        @Schema(description = "현재 조각 세션에 쌓인 탭 수예요. 이름은 상자 시절 그대로지만 키캡 조각 진행도를 담는 현역 필드예요. 마지막 탭 뒤 유휴 시간이 지나 세션이 만료됐으면 0이에요.", example = "45")
        long boxProgressTapCount,
        @Schema(description = "다음 조각이 나오는 세션 누적 탭 수예요. 이름은 상자 시절 그대로지만 키캡 조각 진행도를 담는 현역 필드예요. `boxProgressTapCount` 필드 값이 이 값에 닿으면 조각이 하나 나와요.", example = "100")
        int nextBoxRequiredTapCount,
        @Schema(description = "앞으로 300탭 안에서 클릭 크리티컬이 터질 탭 예정표예요. 장착한 키캡에 클릭 크리티컬이 없거나 패시브가 꺼져 있으면 null이에요.", nullable = true)
        ClickCriticalScheduleResponse clickCriticalSchedule,
        @Schema(description = "오늘 직접 탭, 클릭 크리티컬, 자동 클릭을 합한 클릭 수예요. 탭 배치 응답의 같은 필드와 같은 값이에요. 이 조회는 자동 클릭을 정산하지 않아서, 아직 정산되지 않은 자동 클릭은 다음 탭 배치 뒤에 더해져요.", example = "3823")
        long effectiveTapCountToday
) {
}

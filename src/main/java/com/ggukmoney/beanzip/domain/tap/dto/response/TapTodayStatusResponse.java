package com.ggukmoney.beanzip.domain.tap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "오늘 탭 상태 응답")
public record TapTodayStatusResponse(
        @Schema(description = "기준 일자", example = "2026-07-15")
        LocalDate date,
        @Schema(description = "오늘 인정된 탭 수", example = "120")
        int validTapCount,
        @Schema(description = "오늘 지급된 포인트", example = "12")
        int pointEarnedToday,
        @Schema(description = "다음 포인트까지 남은 탭 수", example = "10")
        int remainingTapsToNextPoint,
        @Schema(description = "다음 키캡 상자까지 남은 탭 수", example = "80")
        int remainingTapsToNextBox
) {
}

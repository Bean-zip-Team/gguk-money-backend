package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "키캡 상자 상태 응답")
public record KeycapBoxStatusResponse(
        @Schema(description = "보유 상자 수", example = "2")
        int boxBalance,
        @Schema(description = "무료 개봉 가능 여부", example = "true")
        boolean canFreeOpen,
        @Schema(description = "광고 개봉 가능 여부", example = "true")
        boolean canAdOpen,
        @Schema(description = "무료와 광고 개봉을 모두 사용해 공통 주기 충전 중인지 여부", example = "false")
        boolean charging,
        @Schema(description = "charging=true일 때만 반환되는 다음 공통 충전 시각", example = "2026-07-16T01:00:00Z")
        Instant nextRechargeAt,
        @Schema(description = "현재 상자 진행 탭 수", example = "45")
        long boxProgressTapCount,
        @Schema(description = "다음 상자 획득 필요 탭 수", example = "100")
        int nextBoxRequiredTapCount
) {
}

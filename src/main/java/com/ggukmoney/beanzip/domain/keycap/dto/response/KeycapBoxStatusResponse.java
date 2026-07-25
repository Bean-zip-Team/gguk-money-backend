package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "키캡 상자 상태 응답")
public record KeycapBoxStatusResponse(
        @Schema(description = "현재 보유한 키캡 상자 수", example = "2")
        int boxBalance,
        @Schema(description = "상자를 보유하고 있으며 무료 개봉 한도가 남아 있는지 여부", example = "true")
        boolean canFreeOpen,
        @Schema(description = "상자를 보유하고 있으며 광고 개봉 한도가 남아 있는지 여부", example = "true")
        boolean canAdOpen,
        @Schema(description = "상자 보유 여부와 무관하게 무료와 광고 개봉 횟수를 모두 소진해 공통 주기 충전 중인지 여부", example = "false")
        boolean charging,
        @Schema(description = "charging=true일 때 다음 공통 충전 시각이며 charging=false일 때 null", example = "2026-07-16T01:00:00Z")
        Instant nextRechargeAt,
        @Schema(description = "현재 상자 진행 탭 수", example = "45")
        long boxProgressTapCount,
        @Schema(description = "다음 상자 획득 필요 탭 수", example = "100")
        int nextBoxRequiredTapCount
) {
}

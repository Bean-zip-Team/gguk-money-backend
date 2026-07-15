package com.ggukmoney.beanzip.global.config.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "앱 설정 응답")
public record AppConfigResponse(
        @Schema(description = "포인트 정책")
        PointPolicy pointPolicy,
        @Schema(description = "키캡 상자 정책")
        BoxPolicy boxPolicy,
        @Schema(description = "부스터 정책")
        BoosterPolicy boosterPolicy
) {

    @Schema(description = "포인트 정책")
    public record PointPolicy(
            @Schema(description = "일일 포인트 적립 한도", example = "20")
            int dailyLimit
    ) {
    }

    @Schema(description = "키캡 상자 정책")
    public record BoxPolicy(
            @Schema(description = "기본 상자 획득 필요 탭 수", example = "200")
            int baseRequiredTapCount
    ) {
    }

    @Schema(description = "부스터 정책")
    public record BoosterPolicy(
            @Schema(description = "부스터 지속 시간(초)", example = "300")
            int durationSeconds,
            @Schema(description = "일일 부스터 활성화 한도", example = "3")
            int dailyLimit
    ) {
    }
}

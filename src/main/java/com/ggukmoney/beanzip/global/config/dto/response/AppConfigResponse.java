package com.ggukmoney.beanzip.global.config.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

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
            @Schema(description = "상자 세션 내 1~5번째 상자까지 필요한 탭 수(순서대로)", example = "[25, 35, 50, 70, 100]")
            List<Integer> sessionStepTapCounts,
            @Schema(description = "6번째 상자부터 이후 매 상자마다 필요한 탭 수", example = "180")
            int tailStepTapCount,
            @Schema(description = "상자 세션이 리셋되는 유휴 시간(초). 마지막 탭 이후 이만큼 쉬면 스텝이 처음으로 돌아간다.", example = "1800")
            int sessionIdleTimeoutSeconds
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

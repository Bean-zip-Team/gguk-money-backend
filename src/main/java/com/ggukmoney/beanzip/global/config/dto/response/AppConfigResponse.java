package com.ggukmoney.beanzip.global.config.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "앱 설정 응답이에요.")
public record AppConfigResponse(
        @Schema(description = "포인트 정책이에요.")
        PointPolicy pointPolicy,
        @Schema(description = "키캡 조각 정책이에요. 탭 몇 번에 조각 하나가 나오는지 정해요. 이름은 상자 시절 그대로예요(BEA-329).")
        BoxPolicy boxPolicy,
        @Schema(description = "부스터 정책이에요.")
        BoosterPolicy boosterPolicy
) {

    @Schema(description = "포인트 정책이에요.")
    public record PointPolicy(
            @Schema(description = "하루에 탭으로 적립할 수 있는 포인트 한도예요.", example = "20")
            int dailyLimit
    ) {
    }

    @Schema(description = "키캡 조각 정책이에요. 탭 몇 번에 조각 하나가 나오는지 정해요. 이름은 상자 시절 그대로예요(BEA-329).")
    public record BoxPolicy(
            @Schema(description = "한 세션에서 1번째부터 5번째 조각까지 각 조각에 필요한 탭 수예요. 배열 순서가 조각 순서예요. 각 값은 직전 조각이 나온 뒤부터 세는 탭 수예요.", example = "[25, 35, 50, 70, 100]")
            List<Integer> sessionStepTapCounts,
            @Schema(description = "6번째 조각부터 조각 하나마다 필요한 탭 수예요.", example = "180")
            int tailStepTapCount,
            @Schema(description = "조각 진행 세션이 리셋되는 유휴 시간이에요. 단위는 초예요. 마지막 탭 뒤 이만큼 쉬면 스텝이 처음으로 돌아가요.", example = "1800")
            int sessionIdleTimeoutSeconds
    ) {
    }

    @Schema(description = "부스터 정책이에요.")
    public record BoosterPolicy(
            @Schema(description = "부스터 지속 시간이에요. 단위는 초예요.", example = "300")
            int durationSeconds,
            @Schema(description = "일일 부스터 활성화 한도예요. 달력 날짜가 아니라 최근 `tap.booster.limitWindowSeconds` 설정 기간(기본 86400초) 동안 시작한 부스터 수로 따져요.", example = "3")
            int dailyLimit
    ) {
    }
}

package com.ggukmoney.beanzip.domain.onboarding.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "온보딩 키캡 상자 개봉 요청이에요.")
public record OnboardingKeycapBoxOpenRequest(
        @Schema(description = "온보딩 탭 세션 ID예요. 같은 ID로 다른 탭 이벤트를 보내면 409를 돌려줘요.", example = "11111111-1111-1111-1111-111111111111")
        @NotNull(message = "tapSessionId가 필요합니다.")
        UUID tapSessionId,

        @Schema(description = "온보딩 탭 이벤트 목록이에요. 1개 이상 필요해요. 개수는 `onboarding.reward.requiredTapCount` 설정값과 같아야 해요.")
        @NotNull(message = "tapEvents가 필요합니다.")
        @Size(min = 1, message = "tapEvents가 필요합니다.")
        List<@Valid TapEvent> tapEvents
) {

    @Schema(description = "온보딩 탭 이벤트예요.")
    public record TapEvent(
            @Schema(description = "탭 이벤트 순번이에요. 1부터 중복이나 빈 번호 없이 이어져야 해요.", example = "1")
            @NotNull(message = "sequence가 필요합니다.")
            Integer sequence,

            @Schema(description = "탭 발생 시각이에요. 순번이 뒤인 이벤트는 앞 이벤트보다 이르면 안 돼요.", example = "2026-07-15T01:00:00Z")
            @NotNull(message = "occurredAt이 필요합니다.")
            Instant occurredAt
    ) {
    }
}

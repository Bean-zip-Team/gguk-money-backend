package com.ggukmoney.beanzip.domain.onboarding.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "온보딩 키캡 상자 개봉 요청")
public record OnboardingKeycapBoxOpenRequest(
        @Schema(description = "온보딩 탭 세션 ID", example = "11111111-1111-1111-1111-111111111111")
        @NotNull(message = "tapSessionId가 필요합니다.")
        UUID tapSessionId,

        @Schema(description = "온보딩 탭 이벤트 목록. 1개 이상 필요합니다.")
        @NotNull(message = "tapEvents가 필요합니다.")
        @Size(min = 1, message = "tapEvents가 필요합니다.")
        List<@Valid TapEvent> tapEvents
) {

    @Schema(description = "온보딩 탭 이벤트")
    public record TapEvent(
            @Schema(description = "탭 이벤트 순번", example = "1")
            @NotNull(message = "sequence가 필요합니다.")
            Integer sequence,

            @Schema(description = "탭 발생 시각", example = "2026-07-15T01:00:00Z")
            @NotNull(message = "occurredAt이 필요합니다.")
            Instant occurredAt
    ) {
    }
}

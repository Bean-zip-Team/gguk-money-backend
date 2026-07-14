package com.ggukmoney.beanzip.domain.onboarding.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OnboardingKeycapBoxOpenRequest(
        @NotNull(message = "tapSessionId가 필요합니다.")
        UUID tapSessionId,

        @NotNull(message = "tapEvents가 필요합니다.")
        @Size(min = 1, message = "tapEvents가 필요합니다.")
        List<@Valid TapEvent> tapEvents
) {

    public record TapEvent(
            @NotNull(message = "sequence가 필요합니다.")
            Integer sequence,

            @NotNull(message = "occurredAt이 필요합니다.")
            Instant occurredAt
    ) {
    }
}

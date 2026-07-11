package com.ggukmoney.beanzip.domain.tap.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TapBatchSubmitRequest(
        @NotNull(message = "tapSessionId가 필요합니다.")
        UUID tapSessionId,

        @NotNull(message = "sequence가 필요합니다.")
        @Min(value = 0, message = "sequence는 0 이상이어야 합니다.")
        Long sequence,

        @NotNull(message = "submittedCount가 필요합니다.")
        @Min(value = 1, message = "submittedCount는 1 이상이어야 합니다.")
        Integer submittedCount
) {
}

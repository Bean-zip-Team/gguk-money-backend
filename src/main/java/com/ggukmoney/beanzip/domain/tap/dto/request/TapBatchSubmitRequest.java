package com.ggukmoney.beanzip.domain.tap.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "탭 배치 제출 요청")
public record TapBatchSubmitRequest(
        @Schema(description = "탭 세션 ID", example = "11111111-1111-1111-1111-111111111111")
        @NotNull(message = "tapSessionId가 필요합니다.")
        UUID tapSessionId,

        @Schema(description = "클라이언트 배치 순번. 0 이상", example = "0")
        @NotNull(message = "sequence가 필요합니다.")
        @Min(value = 0, message = "sequence는 0 이상이어야 합니다.")
        Long sequence,

        @Schema(description = "제출한 탭 수. 1 이상", example = "10")
        @NotNull(message = "submittedCount가 필요합니다.")
        @Min(value = 1, message = "submittedCount는 1 이상이어야 합니다.")
        Integer submittedCount
) {
}

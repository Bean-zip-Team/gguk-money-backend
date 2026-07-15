package com.ggukmoney.beanzip.domain.tap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "탭 배치 제출 응답")
public record TapBatchSubmitResponse(
        @Schema(description = "인정된 탭 수", example = "10")
        int acceptedCount,
        @Schema(description = "지급된 포인트", example = "1")
        int pointsAwarded,
        @Schema(description = "드롭된 키캡 상자 수", example = "0")
        int boxesDropped,
        @Schema(description = "제출 후 포인트 잔액", example = "15")
        long balance
) {
}

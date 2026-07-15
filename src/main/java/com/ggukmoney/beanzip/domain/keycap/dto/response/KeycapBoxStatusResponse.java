package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "키캡 상자 상태 응답")
public record KeycapBoxStatusResponse(
        @Schema(description = "보유 상자 수", example = "2")
        int boxBalance,
        @Schema(description = "무료 개봉권 수", example = "1")
        int freeOpenTicketCount,
        @Schema(description = "현재 상자 진행 탭 수", example = "45")
        long boxProgressTapCount,
        @Schema(description = "다음 상자 획득 필요 탭 수", example = "100")
        int nextBoxRequiredTapCount
) {
}

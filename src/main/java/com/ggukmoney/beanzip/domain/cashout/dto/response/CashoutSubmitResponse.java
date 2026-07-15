package com.ggukmoney.beanzip.domain.cashout.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "출금 신청 응답")
public record CashoutSubmitResponse(
        @Schema(description = "출금 요청 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID cashoutId,
        @Schema(description = "차감 포인트", example = "134")
        long pointAmount,
        @Schema(description = "전환 Toss 포인트 금액", example = "93")
        long tossPointAmount,
        @Schema(description = "출금 상태", example = "PROCESSING")
        String status,
        @Schema(description = "신청 시각", example = "2026-07-15T01:00:00Z")
        Instant requestedAt
) {
}

package com.ggukmoney.beanzip.domain.point.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "포인트 원장 항목")
public record PointLedgerItemResponse(
        @Schema(description = "원장 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID ledgerId,
        @Schema(description = "원장 항목 유형", example = "CREDIT")
        String entryType,
        @Schema(description = "포인트 변동량", example = "10")
        long amount,
        @Schema(description = "원장 사유", example = "TAP")
        String reason,
        @Schema(description = "발생 시각", example = "2026-07-15T01:00:00Z")
        Instant occurredAt
) {
}

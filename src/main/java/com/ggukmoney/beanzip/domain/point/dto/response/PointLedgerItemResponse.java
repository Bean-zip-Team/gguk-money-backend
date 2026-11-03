package com.ggukmoney.beanzip.domain.point.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "포인트 원장 항목이에요.")
public record PointLedgerItemResponse(
        @Schema(description = "원장 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID ledgerId,
        @Schema(description = "원장 항목 유형이에요. `CREDIT`은 적립, `DEBIT`은 차감, `REVERSAL`은 출금 실패에 따른 환불이에요.", example = "CREDIT")
        String entryType,
        @Schema(description = "변동한 포인트 양이에요. 늘었는지 줄었는지는 `entryType` 필드로 구분해요.", example = "10")
        long amount,
        @Schema(description = "원장 사유 코드예요. 예를 들어 `TAP_REWARD`는 탭 보상, `TAP_OVERFLOW_REWARD`는 일일 상한 이후의 희박 탭 보상, `CASHOUT`은 출금 차감, `CASHOUT_FAILED`는 출금 실패 환불이에요.", example = "TAP_REWARD")
        String reason,
        @Schema(description = "발생 시각이에요.", example = "2026-07-15T01:00:00Z")
        Instant occurredAt
) {
}

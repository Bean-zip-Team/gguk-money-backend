package com.ggukmoney.beanzip.domain.cashout.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "출금 견적 응답")
public record CashoutQuoteResponse(
        @Schema(description = "현재 포인트 잔액", example = "134")
        long pointBalance,
        @Schema(description = "전환 예상 Toss 포인트 금액", example = "2")
        long tossPointAmount,
        @Schema(description = "실제로 차감될 포인트. 버림으로 사라지던 잔돈은 남는다.", example = "100")
        long redeemablePoint,
        @Schema(description = "출금 후 남는 포인트", example = "34")
        long remainingPoint,
        @Schema(description = "최소 출금 가능 포인트", example = "10")
        int minimumPoint,
        @Schema(description = "전환 비율")
        RateInfo rate,
        @Schema(description = "출금 가능 여부", example = "true")
        boolean eligible
) {
    @Schema(description = "출금 전환 비율")
    public record RateInfo(BigDecimal pointToKrw) {
    }
}

package com.ggukmoney.beanzip.domain.cashout.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "출금 견적 응답이에요.")
public record CashoutQuoteResponse(
        @Schema(description = "현재 포인트 잔액이에요.", example = "134")
        long pointBalance,
        @Schema(description = "지금 출금하면 받을 예상 Toss 포인트 금액(원)이에요. 잔액에 전환 비율을 곱한 뒤 소수점 아래를 버려요.", example = "2")
        long tossPointAmount,
        @Schema(description = "출금하면 실제로 차감할 포인트예요. 지급액을 만드는 데 필요한 최소 포인트만 차감해요. 버림으로 사라지던 잔돈은 잔액에 남아요.", example = "100")
        long redeemablePoint,
        @Schema(description = "출금한 뒤 남는 포인트예요.", example = "34")
        long remainingPoint,
        @Schema(description = "출금에 필요한 최소 포인트예요.", example = "50")
        int minimumPoint,
        @Schema(description = "출금 전환 비율이에요.")
        RateInfo rate,
        @Schema(description = "출금 가능 여부예요. 잔액이 최소 출금 포인트 이상이고 차감할 포인트가 0보다 크면 `true`예요.", example = "true")
        boolean eligible
) {
    @Schema(description = "출금 전환 비율 정보예요.")
    public record RateInfo(
            @Schema(description = "1포인트당 받는 Toss 포인트 금액(원)이에요. 0.02면 50포인트가 1원이 돼요.", example = "0.02")
            BigDecimal pointToKrw
    ) {
    }
}

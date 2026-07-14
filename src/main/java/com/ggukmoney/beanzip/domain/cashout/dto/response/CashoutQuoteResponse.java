package com.ggukmoney.beanzip.domain.cashout.dto.response;

public record CashoutQuoteResponse(
        long pointBalance,
        long tossPointAmount,
        int minimumPoint,
        RateInfo rate,
        boolean eligible
) {
    public record RateInfo(double pointToKrw) {
    }
}

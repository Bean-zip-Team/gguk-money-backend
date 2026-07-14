package com.ggukmoney.beanzip.domain.cashout.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CashoutSubmitResponse(
        UUID cashoutId,
        long pointAmount,
        long tossPointAmount,
        String status,
        Instant requestedAt
) {
}

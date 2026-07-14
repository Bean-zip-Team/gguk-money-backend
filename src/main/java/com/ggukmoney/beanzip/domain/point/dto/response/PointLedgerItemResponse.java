package com.ggukmoney.beanzip.domain.point.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PointLedgerItemResponse(
        UUID ledgerId,
        String entryType,
        long amount,
        String reason,
        Instant occurredAt
) {
}

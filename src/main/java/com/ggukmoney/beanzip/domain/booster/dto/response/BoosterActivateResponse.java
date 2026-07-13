package com.ggukmoney.beanzip.domain.booster.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BoosterActivateResponse(
        UUID boosterId,
        boolean active,
        BigDecimal multiplier,
        Instant startsAt,
        Instant endsAt,
        int remainingDailyCount
) {
}

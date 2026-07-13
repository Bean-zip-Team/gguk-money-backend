package com.ggukmoney.beanzip.domain.booster.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record BoosterStatusResponse(
        boolean active,
        BigDecimal multiplier,
        long remainingSeconds,
        Instant endsAt,
        int remainingDailyCount
) {
}

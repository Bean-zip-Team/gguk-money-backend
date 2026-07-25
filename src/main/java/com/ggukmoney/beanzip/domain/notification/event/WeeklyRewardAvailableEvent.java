package com.ggukmoney.beanzip.domain.notification.event;

import java.time.Instant;
import java.util.UUID;

public record WeeklyRewardAvailableEvent(
        UUID userId,
        String rewardCycleKey,
        Instant availableAt
) {
}

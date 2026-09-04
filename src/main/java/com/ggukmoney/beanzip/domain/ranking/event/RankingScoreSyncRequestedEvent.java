package com.ggukmoney.beanzip.domain.ranking.event;

import java.time.Instant;
import java.util.UUID;

public record RankingScoreSyncRequestedEvent(
        UUID userId,
        Instant occurredAt
) {
}

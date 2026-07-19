package com.ggukmoney.beanzip.domain.ranking.event;

import java.time.Instant;
import java.util.UUID;

public record RankingScoreChangedEvent(
        Long seasonId,
        UUID userId,
        long score,
        String regionCode,
        String previousRegionCode,
        boolean participantEligible,
        Instant occurredAt
) {
}

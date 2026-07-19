package com.ggukmoney.beanzip.domain.ranking.event;

import java.util.UUID;

public record RankingScoreSyncRequestedEvent(UUID userId) {
}

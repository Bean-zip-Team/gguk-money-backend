package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreSyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RankingScoreSyncRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(RankingScoreSyncRequestedListener.class);

    private final RankingProjectionService rankingProjectionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RankingScoreSyncRequestedEvent event) {
        try {
            rankingProjectionService.syncLatestWeeklyScore(event.userId(), event.occurredAt());
        } catch (RuntimeException exception) {
            log.error("Failed to synchronize ranking projection userId={}", event.userId(), exception);
        }
    }
}

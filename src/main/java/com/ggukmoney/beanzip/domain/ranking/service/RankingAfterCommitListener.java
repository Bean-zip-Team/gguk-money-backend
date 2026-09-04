package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreChangedEvent;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RankingAfterCommitListener {

    private static final Logger log = LoggerFactory.getLogger(RankingAfterCommitListener.class);

    private final RankingRedisRepository redisRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RankingScoreChangedEvent event) {
        try {
            if (event.participantEligible()) {
                redisRepository.updateScore(
                        event.seasonId(),
                        event.userId(),
                        event.score(),
                        event.regionCode(),
                        event.previousRegionCode()
                );
            } else {
                redisRepository.removeParticipant(event.seasonId(), event.userId(), event.regionCode());
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to update ranking Redis projection seasonId={} userId={}",
                    event.seasonId(),
                    event.userId(),
                    exception
            );
        }
    }
}

package com.ggukmoney.beanzip.domain.notification.event;

import com.ggukmoney.beanzip.domain.notification.repository.WeeklyRankingResetNotificationBatchRepository;
import com.ggukmoney.beanzip.domain.ranking.event.WeeklyRankingSeasonClosedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyRankingSeasonClosedNotificationListener {

    private final WeeklyRankingResetNotificationBatchRepository batchRepository;
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onSeasonClosed(WeeklyRankingSeasonClosedEvent event) {
        int inserted = batchRepository.insertIfAbsent(event.seasonId(), clock.instant());
        log.info("Weekly rank reset notification batch prepared. seasonId={}, created={}", event.seasonId(), inserted == 1);
    }
}

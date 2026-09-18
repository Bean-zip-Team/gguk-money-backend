package com.ggukmoney.beanzip.domain.ranking.boost;

import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemRankingBoostScheduler {
    private final SystemRankingBoostTransactionService transactions;
    private final NotificationDeliveryService notifications;
    private final Clock clock;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    public void tick() {
        try {
            var now = clock.instant();
            if (transactions.plan(now).isEmpty()) return;
            transactions.advance(now).ifPresent(run ->
                    transactions.claimDispatch(run.getRunDate(), clock.instant())
                            .ifPresent(notifications::dispatchPreparedRankChange));
        } catch (RuntimeException exception) {
            // The persisted plan allows a safe retry of rolled-back DB work on the next tick.
            // A claimed/unknown provider send is deliberately NOT automatically retried.
            log.error("SYSTEM_RANKING_BOOST tick failed; inspect daily audit and delivery state", exception);
        }
    }
}

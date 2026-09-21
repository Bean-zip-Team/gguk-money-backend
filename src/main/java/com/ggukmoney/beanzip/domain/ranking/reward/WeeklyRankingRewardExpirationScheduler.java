package com.ggukmoney.beanzip.domain.ranking.reward;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyRankingRewardExpirationScheduler {

    private final WeeklyRankingRewardExpirationService expirationService;
    private final Clock clock;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    public void expireDueRewards() {
        try {
            expirationService.expireDue(clock.instant());
        } catch (RuntimeException exception) {
            log.error("Weekly ranking reward expiration tick failed", exception);
        }
    }
}

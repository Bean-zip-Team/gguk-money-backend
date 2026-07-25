package com.ggukmoney.beanzip.domain.ranking.scheduler;

import com.ggukmoney.beanzip.domain.ranking.service.RankingProperties;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class RankingSeasonRolloverScheduler {

    private final RankingSeasonService seasonService;
    private final RankingProperties rankingProperties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "#{@rankingProperties.weeklyRolloverInterval().toMillis()}")
    public void rolloverWeeklySeasons() {
        seasonService.rolloverWeeklySeasons(clock.instant());
    }
}

package com.ggukmoney.beanzip.domain.ranking.scheduler;

import com.ggukmoney.beanzip.domain.ranking.service.RankingProperties;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RankingSeasonRolloverSchedulerTest {

    private final RankingSeasonService seasonService = mock(RankingSeasonService.class);
    private final RankingProperties rankingProperties = new RankingProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T15:00:00Z"), ZoneOffset.UTC);
    private final RankingSeasonRolloverScheduler scheduler =
            new RankingSeasonRolloverScheduler(seasonService, rankingProperties, clock);

    @Test
    void delegatesRolloverUsingInjectedClock() {
        scheduler.rolloverWeeklySeasons();

        verify(seasonService).rolloverWeeklySeasons(Instant.parse("2026-07-26T15:00:00Z"));
    }

    @Test
    void rolloverUsesRankingPropertiesFixedDelay() throws NoSuchMethodException {
        Method method = RankingSeasonRolloverScheduler.class.getMethod("rolloverWeeklySeasons");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("#{@rankingProperties.weeklyRolloverInterval().toMillis()}");
    }
}

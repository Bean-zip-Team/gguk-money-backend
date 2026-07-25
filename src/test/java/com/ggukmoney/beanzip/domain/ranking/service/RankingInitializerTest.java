package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisMeta;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingInitializerTest {

    private final RankingSeasonService seasonService = mock(RankingSeasonService.class);
    private final RankingBackfillService backfillService = mock(RankingBackfillService.class);
    private final RankingRebuildService rebuildService = mock(RankingRebuildService.class);
    private final RankingRedisRepository redisRepository = mock(RankingRedisRepository.class);
    private final RankingProperties properties = new RankingProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T01:00:00Z"), ZoneOffset.UTC);
    private final RankingInitializer initializer = new RankingInitializer(
            seasonService,
            backfillService,
            rebuildService,
            redisRepository,
            properties,
            clock
    );

    @Test
    void redisFailureDoesNotFailApplicationStartup() {
        when(seasonService.findActiveWeeklySeason()).thenReturn(Optional.empty());
        when(redisRepository.tryAcquireInitializationLock(anyString(), eq(properties.initializationLockTtl())))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> initializer.run()).doesNotThrowAnyException();
    }

    @Test
    void rechecksInitializationNeedAfterLockAndSkipsWhenReady() {
        RankingSeason season = season();
        RankingRedisMeta ready = new RankingRedisMeta(
                RankingRedisMeta.STATE_READY,
                Instant.parse("2026-07-19T00:59:00Z"),
                Instant.parse("2026-07-19T00:50:00Z"),
                1,
                1,
                null,
                Instant.parse("2026-07-19T00:58:00Z"),
                10L
        );
        when(seasonService.findActiveWeeklySeason())
                .thenReturn(Optional.of(season))
                .thenReturn(Optional.of(season));
        when(redisRepository.findReadyMeta(1L, 1, properties.maxStaleness(), clock.instant()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ready));
        when(redisRepository.tryAcquireInitializationLock(anyString(), eq(properties.initializationLockTtl())))
                .thenReturn(true);

        initializer.run();

        verify(backfillService, never()).backfillActiveWeeklySeason(season);
        verify(rebuildService, never()).rebuild(season, "startup-initializer");
    }

    @Test
    void backfillsAndRebuildsWhenInitializationIsNeeded() {
        RankingSeason season = season();
        when(seasonService.findActiveWeeklySeason()).thenReturn(Optional.empty());
        when(seasonService.ensureCurrentWeeklySeason(clock.instant())).thenReturn(season);
        when(redisRepository.tryAcquireInitializationLock(anyString(), eq(properties.initializationLockTtl())))
                .thenReturn(true);
        when(redisRepository.findReadyMeta(1L, 1, properties.maxStaleness(), clock.instant()))
                .thenReturn(Optional.empty());
        when(rebuildService.rebuild(season, "startup-initializer")).thenReturn(true);

        initializer.run();

        verify(backfillService).backfillActiveWeeklySeason(season);
        verify(rebuildService).rebuild(season, "startup-initializer");
    }

    private RankingSeason season() {
        RankingSeason season = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        ReflectionTestUtils.setField(season, "id", 1L);
        return season;
    }
}

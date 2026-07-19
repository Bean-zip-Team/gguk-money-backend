package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisMeta;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
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
        when(seasonService.findActiveAllTimeSeason()).thenReturn(Optional.empty());
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
        when(seasonService.findActiveAllTimeSeason())
                .thenReturn(Optional.of(season))
                .thenReturn(Optional.of(season));
        when(redisRepository.findReadyMeta(1L, 1, properties.maxStaleness(), clock.instant()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ready));
        when(redisRepository.tryAcquireInitializationLock(anyString(), eq(properties.initializationLockTtl())))
                .thenReturn(true);

        initializer.run();

        verify(backfillService, never()).backfillActiveAllTimeFromTapProgress();
        verify(rebuildService, never()).rebuild(season, "startup-initializer");
    }

    @Test
    void backfillsAndRebuildsWhenInitializationIsNeeded() {
        RankingSeason season = season();
        when(seasonService.findActiveAllTimeSeason()).thenReturn(Optional.empty());
        when(seasonService.getOrCreateActiveAllTimeSeason()).thenReturn(season);
        when(redisRepository.tryAcquireInitializationLock(anyString(), eq(properties.initializationLockTtl())))
                .thenReturn(true);
        when(rebuildService.rebuild(season, "startup-initializer")).thenReturn(true);

        initializer.run();

        verify(backfillService).backfillActiveAllTimeFromTapProgress();
        verify(rebuildService).rebuild(season, "startup-initializer");
    }

    private RankingSeason season() {
        RankingSeason season = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        ReflectionTestUtils.setField(season, "id", 1L);
        return season;
    }
}

package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingRebuildServiceTest {

    private final RankingSeasonService seasonService = mock(RankingSeasonService.class);
    private final RankingEntryRepository entryRepository = mock(RankingEntryRepository.class);
    private final RankingRedisRepository redisRepository = mock(RankingRedisRepository.class);
    private final RankingProperties properties = new RankingProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T01:00:00Z"), ZoneOffset.UTC);
    private final RankingRebuildService service = new RankingRebuildService(
            seasonService,
            entryRepository,
            redisRepository,
            properties,
            clock
    );

    @Test
    void treatsEmptyParticipantRebuildAsSuccessfulAndDoesNotRequireTempZSet() {
        RankingSeason season = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        ReflectionTestUtils.setField(season, "id", 1L);
        when(redisRepository.tryAcquireRebuildLock(eq(1L), anyString(), eq(properties.rebuildLockTtl()))).thenReturn(true);
        when(redisRepository.tempGlobalKey(eq(1L), anyString())).thenReturn("temp");
        when(entryRepository.countParticipants(season)).thenReturn(0L);
        when(entryRepository.findLastCursorEntry(season, PageRequest.of(0, 1))).thenReturn(List.of());
        when(redisRepository.isRebuildLockOwned(eq(1L), anyString())).thenReturn(true);
        when(redisRepository.swapTempGlobalToLive(
                eq(1L),
                eq("temp"),
                eq(0L),
                eq(1),
                eq(Instant.parse("2026-07-19T01:00:00Z")),
                eq(Instant.EPOCH),
                eq(0L),
                anyString()
        )).thenReturn(true);

        boolean result = service.rebuild(season, "test");

        assertThat(result).isTrue();
        verify(redisRepository, never()).addToTempGlobal(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        verify(redisRepository).swapTempGlobalToLive(
                eq(1L),
                eq("temp"),
                eq(0L),
                eq(1),
                eq(Instant.parse("2026-07-19T01:00:00Z")),
                eq(Instant.EPOCH),
                eq(0L),
                anyString()
        );
    }

    @Test
    void storesHighWaterFromRebuildStartRatherThanLatestCursorAfterLoading() {
        RankingSeason season = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        ReflectionTestUtils.setField(season, "id", 1L);
        com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry highWater =
                mock(com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry.class);
        when(highWater.getUpdatedAt()).thenReturn(Instant.parse("2026-07-19T00:10:00Z"));
        when(highWater.getId()).thenReturn(10L);
        com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry changedDuringRebuild =
                mock(com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry.class);
        com.ggukmoney.beanzip.domain.user.entity.AppUser user = mock(com.ggukmoney.beanzip.domain.user.entity.AppUser.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(changedDuringRebuild.getUser()).thenReturn(user);
        when(changedDuringRebuild.getScore()).thenReturn(200L);
        when(entryRepository.findLastCursorEntry(season, PageRequest.of(0, 1)))
                .thenReturn(List.of(highWater))
                .thenReturn(List.of(changedDuringRebuild));
        when(redisRepository.tryAcquireRebuildLock(eq(1L), anyString(), eq(properties.rebuildLockTtl()))).thenReturn(true);
        when(redisRepository.tempGlobalKey(eq(1L), anyString())).thenReturn("temp");
        when(redisRepository.isRebuildLockOwned(eq(1L), anyString())).thenReturn(true);
        when(entryRepository.countParticipants(season)).thenReturn(1L);
        when(entryRepository.findRebuildEntries(season, PageRequest.of(0, properties.pageSize())))
                .thenReturn(List.of(changedDuringRebuild));
        when(redisRepository.swapTempGlobalToLive(
                eq(1L),
                eq("temp"),
                eq(1L),
                eq(1),
                eq(Instant.parse("2026-07-19T01:00:00Z")),
                eq(Instant.parse("2026-07-19T00:10:00Z")),
                eq(10L),
                anyString()
        )).thenReturn(true);

        boolean result = service.rebuild(season, "test");

        assertThat(result).isTrue();
        verify(redisRepository).swapTempGlobalToLive(
                eq(1L),
                eq("temp"),
                eq(1L),
                eq(1),
                eq(Instant.parse("2026-07-19T01:00:00Z")),
                eq(Instant.parse("2026-07-19T00:10:00Z")),
                eq(10L),
                anyString()
        );
    }

    @Test
    void failsRebuildBeforeSwapWhenLockOwnershipWasLost() {
        RankingSeason season = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        ReflectionTestUtils.setField(season, "id", 1L);
        when(redisRepository.tryAcquireRebuildLock(eq(1L), anyString(), eq(properties.rebuildLockTtl()))).thenReturn(true);
        when(redisRepository.tempGlobalKey(eq(1L), anyString())).thenReturn("temp");
        when(redisRepository.isRebuildLockOwned(eq(1L), anyString())).thenReturn(false);
        when(entryRepository.countParticipants(season)).thenReturn(0L);
        when(entryRepository.findLastCursorEntry(season, PageRequest.of(0, 1))).thenReturn(List.of());

        boolean result = service.rebuild(season, "test");

        assertThat(result).isFalse();
        verify(redisRepository, never()).swapTempGlobalToLive(any(), anyString(), anyLong(), any(Integer.class), any(), any(), anyLong(), anyString());
    }
}

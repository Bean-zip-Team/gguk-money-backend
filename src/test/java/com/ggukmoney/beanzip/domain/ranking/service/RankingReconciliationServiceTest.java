package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisMeta;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingReconciliationServiceTest {

    private final RankingSeasonService seasonService = mock(RankingSeasonService.class);
    private final RankingEntryRepository entryRepository = mock(RankingEntryRepository.class);
    private final RankingRedisRepository redisRepository = mock(RankingRedisRepository.class);
    private final RankingRebuildService rebuildService = mock(RankingRebuildService.class);
    private final RankingProperties properties = new RankingProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T01:00:00Z"), ZoneOffset.UTC);
    private final RankingReconciliationService service = new RankingReconciliationService(
            seasonService,
            entryRepository,
            redisRepository,
            rebuildService,
            properties,
            clock
    );

    @Test
    void advancesCompositeWatermarkAfterSameTimestampRowsBeyondPageBoundary() {
        RankingSeason season = season();
        Instant sameUpdatedAt = Instant.parse("2026-07-19T00:30:00Z");
        RankingRedisMeta meta = new RankingRedisMeta(
                RankingRedisMeta.STATE_READY,
                Instant.parse("2026-07-19T00:59:00Z"),
                null,
                2,
                1,
                null,
                Instant.parse("2026-07-19T00:29:00Z"),
                10L
        );
        RankingEntry first = entry(season, UUID.randomUUID(), 100L, sameUpdatedAt, 41L);
        RankingEntry second = entry(season, UUID.randomUUID(), 90L, sameUpdatedAt, 42L);
        when(seasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(redisRepository.findMeta(1L)).thenReturn(Optional.of(meta));
        when(redisRepository.tryAcquireReconciliationLock(eq(1L), anyString(), eq(properties.reconciliationLockTtl())))
                .thenReturn(true);
        when(entryRepository.findChangedEntries(
                eq(season),
                eq(Instant.parse("2026-07-19T00:28:55Z")),
                eq(0L),
                eq(PageRequest.of(0, properties.pageSize()))
        )).thenReturn(List.of(first, second));

        service.reconcileActiveWeekly();

        verify(redisRepository).recordReconciliationSuccess(
                1L,
                1,
                sameUpdatedAt,
                42L,
                Instant.parse("2026-07-19T01:00:00Z")
        );
    }

    @Test
    void processesMultiplePagesInOneCycleAndAdvancesOnlyFinalWatermark() {
        RankingSeason season = season();
        Instant pageOneUpdatedAt = Instant.parse("2026-07-19T00:30:00Z");
        Instant pageTwoUpdatedAt = Instant.parse("2026-07-19T00:31:00Z");
        RankingRedisMeta meta = readyMeta(Instant.parse("2026-07-19T00:29:00Z"), 10L);
        List<RankingEntry> firstPage = entries(season, pageOneUpdatedAt, 11L, properties.pageSize());
        RankingEntry lastEntry = entry(season, UUID.randomUUID(), 100L, pageTwoUpdatedAt, 600L);
        AtomicInteger page = new AtomicInteger();
        when(seasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(redisRepository.findMeta(1L)).thenReturn(Optional.of(meta));
        when(redisRepository.tryAcquireReconciliationLock(eq(1L), anyString(), eq(properties.reconciliationLockTtl())))
                .thenReturn(true);
        when(entryRepository.findChangedEntries(eq(season), any(), any(), eq(PageRequest.of(0, properties.pageSize()))))
                .thenAnswer(invocation -> {
                    int current = page.getAndIncrement();
                    if (current == 0) {
                        return firstPage;
                    }
                    if (current == 1) {
                        return List.of(lastEntry);
                    }
                    return List.of();
                });

        service.reconcileActiveWeekly();

        verify(redisRepository).recordReconciliationSuccess(
                1L,
                1,
                pageTwoUpdatedAt,
                600L,
                Instant.parse("2026-07-19T01:00:00Z")
        );
    }

    @Test
    void skipsCycleWhenReconciliationLockIsAlreadyHeld() {
        RankingSeason season = season();
        when(seasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(redisRepository.findMeta(1L)).thenReturn(Optional.of(readyMeta(Instant.parse("2026-07-19T00:29:00Z"), 10L)));
        when(redisRepository.tryAcquireReconciliationLock(eq(1L), anyString(), eq(properties.reconciliationLockTtl())))
                .thenReturn(false);

        service.reconcileActiveWeekly();

        verify(entryRepository, never()).findChangedEntries(any(), any(), any(), any());
        verify(redisRepository, never()).recordReconciliationSuccess(any(), any(Integer.class), any(), any(Long.class), any());
    }

    @Test
    void doesNotAdvanceWatermarkWhenAnyRedisWriteFails() {
        RankingSeason season = season();
        RankingRedisMeta meta = new RankingRedisMeta(
                RankingRedisMeta.STATE_READY,
                Instant.parse("2026-07-19T00:59:00Z"),
                null,
                1,
                1,
                null,
                Instant.parse("2026-07-19T00:29:00Z"),
                10L
        );
        RankingEntry entry = entry(season, UUID.randomUUID(), 100L, Instant.parse("2026-07-19T00:30:00Z"), 41L);
        when(seasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(redisRepository.findMeta(1L)).thenReturn(Optional.of(meta));
        when(redisRepository.tryAcquireReconciliationLock(eq(1L), anyString(), eq(properties.reconciliationLockTtl())))
                .thenReturn(true);
        when(entryRepository.findChangedEntries(any(), any(), any(), any())).thenReturn(List.of(entry));
        doThrow(new IllegalStateException("redis down"))
                .when(redisRepository)
                .updateScore(eq(1L), any(), eq(100L), any(), any());

        service.reconcileActiveWeekly();

        verify(redisRepository, never()).recordReconciliationSuccess(any(), any(Integer.class), any(), any(Long.class), any());
        verify(redisRepository).recordReconciliationFailure(1L, Instant.parse("2026-07-19T01:00:00Z"));
    }

    @Test
    void releasesReconciliationLockWithOwnerTokenAfterCycle() {
        RankingSeason season = season();
        when(seasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(redisRepository.findMeta(1L)).thenReturn(Optional.of(readyMeta(Instant.parse("2026-07-19T00:29:00Z"), 10L)));
        when(redisRepository.tryAcquireReconciliationLock(eq(1L), anyString(), eq(properties.reconciliationLockTtl())))
                .thenReturn(true);
        when(entryRepository.findChangedEntries(any(), any(), any(), any())).thenReturn(List.of());

        service.reconcileActiveWeekly();

        verify(redisRepository).releaseReconciliationLock(eq(1L), anyString());
    }

    private RankingRedisMeta readyMeta(Instant updatedAt, long entryId) {
        return new RankingRedisMeta(
                RankingRedisMeta.STATE_READY,
                Instant.parse("2026-07-19T00:59:00Z"),
                null,
                1,
                1,
                null,
                updatedAt,
                entryId
        );
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

    private RankingEntry entry(RankingSeason season, UUID userId, long score, Instant updatedAt, long id) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(user.getStatus()).thenReturn(AppUser.Status.ACTIVE);
        RankingEntry entry = RankingEntry.createFor(season, user, score, null, updatedAt);
        ReflectionTestUtils.setField(entry, "id", id);
        ReflectionTestUtils.setField(entry, "updatedAt", updatedAt);
        return entry;
    }

    private List<RankingEntry> entries(RankingSeason season, Instant updatedAt, long firstId, int count) {
        return java.util.stream.LongStream.range(0, count)
                .mapToObj(offset -> entry(season, UUID.randomUUID(), 100L, updatedAt, firstId + offset))
                .toList();
    }
}

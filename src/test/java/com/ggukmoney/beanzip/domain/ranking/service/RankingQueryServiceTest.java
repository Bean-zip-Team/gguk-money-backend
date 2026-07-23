package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.dto.response.CurrentRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingItemResponse;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisMeta;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingQueryServiceTest {

    private final RankingSeasonService seasonService = mock(RankingSeasonService.class);
    private final RankingEntryRepository entryRepository = mock(RankingEntryRepository.class);
    private final RankingRedisRepository redisRepository = mock(RankingRedisRepository.class);
    private final RankingProperties properties = new RankingProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T01:00:00Z"), ZoneOffset.UTC);
    private final ZoneId businessZoneId = ZoneId.of("Asia/Seoul");
    private final RankingQueryService service = new RankingQueryService(
            seasonService, entryRepository, redisRepository, properties, clock, businessZoneId
    );

    @Test
    void rejectsLimitOutsideAllowedRange() {
        assertThatThrownBy(() -> service.getCurrentRanking(UUID.randomUUID(), 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMMON_VALIDATION_ERROR");

        assertThatThrownBy(() -> service.getCurrentRanking(UUID.randomUUID(), 101))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMMON_VALIDATION_ERROR");
    }

    @Test
    void acceptsLimitUpToOneHundredForBackwardCompatibility() {
        UUID me = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RankingSeason season = activeSeason();
        when(seasonService.getActiveWeeklySeason()).thenReturn(season);
        when(redisRepository.findReadyMeta(season.getId(), properties.schemaVersion(), properties.maxStaleness(), clock.instant()))
                .thenReturn(Optional.empty());
        when(entryRepository.findTopParticipants(season, 100)).thenReturn(List.of());
        when(entryRepository.findMyParticipant(season, me)).thenReturn(Optional.empty());
        when(entryRepository.countParticipants(season)).thenReturn(0L);

        CurrentRankingResponse response = service.getCurrentRanking(me, 100);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void includesPreviousFinalRankAndRankChangeFromPreviousClosedWeeklySeason() {
        UUID me = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID first = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        RankingSeason season = activeSeason();
        RankingSeason previous = previousSeason();
        when(seasonService.getActiveWeeklySeason()).thenReturn(season);
        when(seasonService.findPreviousClosedWeeklySeason(season)).thenReturn(Optional.of(previous));
        when(redisRepository.findReadyMeta(season.getId(), properties.schemaVersion(), properties.maxStaleness(), clock.instant()))
                .thenReturn(Optional.empty());
        when(entryRepository.findTopParticipants(season, 50)).thenReturn(List.of(row(first, "first", 200L)));
        when(entryRepository.findMyParticipant(season, me)).thenReturn(Optional.empty());
        when(entryRepository.countParticipants(season)).thenReturn(1L);
        when(entryRepository.findFinalRanksByUserIds(previous, List.of(first, me))).thenReturn(List.of(
                new RankingEntryRepository.RankingFinalRankRow(first, 3L),
                new RankingEntryRepository.RankingFinalRankRow(me, 10L)
        ));

        CurrentRankingResponse response = service.getCurrentRanking(me, null);

        assertThat(response.items().get(0).previousRank()).isEqualTo(3L);
        assertThat(response.items().get(0).rankChange()).isEqualTo(2L);
        assertThat(response.myRank().rank()).isNull();
        assertThat(response.myRank().previousRank()).isEqualTo(10L);
        assertThat(response.myRank().rankChange()).isNull();
        assertThat(response.myRank().score()).isZero();
        assertThat(response.myRank().scoreGapToFirst()).isEqualTo(200L);
    }

    @Test
    void usesPostgreSqlFallbackWhenRedisMetaIsMissing() {
        UUID me = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID first = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        RankingSeason season = activeSeason();
        when(seasonService.getActiveWeeklySeason()).thenReturn(season);
        when(redisRepository.findReadyMeta(season.getId(), properties.schemaVersion(), properties.maxStaleness(), clock.instant()))
                .thenReturn(Optional.empty());
        when(entryRepository.findTopParticipants(season, 50)).thenReturn(List.of(row(first, "first", 200L)));
        when(entryRepository.findMyParticipant(season, me)).thenReturn(Optional.of(row(me, "me", 100L)));
        when(entryRepository.countParticipants(season)).thenReturn(2L);
        when(entryRepository.countParticipantsAhead(season, 100L, me.toString())).thenReturn(1L);

        CurrentRankingResponse response = service.getCurrentRanking(me, null);

        assertThat(response.items()).extracting(RankingItemResponse::userId).containsExactly(first);
        assertThat(response.myRank().rank()).isEqualTo(2L);
        assertThat(response.myRank().score()).isEqualTo(100L);
        assertThat(response.myRank().scoreGapToFirst()).isEqualTo(100L);
        assertThat(response.totalParticipantCount()).isEqualTo(2L);
        verify(redisRepository, never()).findTopMembers(season.getId(), 50);
    }

    @Test
    void keepsRedisTieBreakOrderingAndUsesZRevRankPlusOneForMyRank() {
        UUID me = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID lexicographicallyLast = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID lexicographicallyMiddle = UUID.fromString("77777777-7777-7777-7777-777777777777");
        RankingSeason season = activeSeason();
        RankingRedisMeta meta = new RankingRedisMeta(
                RankingRedisMeta.STATE_READY,
                Instant.parse("2026-07-19T00:59:00Z"),
                null,
                3,
                1,
                null,
                Instant.parse("2026-07-19T00:58:00Z"),
                10L
        );
        when(seasonService.getActiveWeeklySeason()).thenReturn(season);
        when(redisRepository.findReadyMeta(1L, 1, properties.maxStaleness(), clock.instant()))
                .thenReturn(Optional.of(meta));
        when(redisRepository.isGlobalZSetMissing(1L)).thenReturn(false);
        when(redisRepository.findParticipantCount(1L)).thenReturn(3L);
        when(redisRepository.findTopMembers(1L, 50)).thenReturn(List.of(
                new RankingRedisRepository.RankingRedisMember(lexicographicallyLast, 100L, 1L),
                new RankingRedisRepository.RankingRedisMember(lexicographicallyMiddle, 100L, 2L),
                new RankingRedisRepository.RankingRedisMember(me, 100L, 3L)
        ));
        when(entryRepository.findParticipantsByUserIds(season, List.of(lexicographicallyLast, lexicographicallyMiddle, me)))
                .thenReturn(List.of(
                        row(me, "me", 999L),
                        row(lexicographicallyMiddle, "middle", 999L),
                        row(lexicographicallyLast, "last", 999L)
                ));
        when(redisRepository.findRank(1L, me)).thenReturn(3L);
        when(redisRepository.findScore(1L, me)).thenReturn(100L);
        when(entryRepository.findMyParticipant(season, me)).thenReturn(Optional.of(row(me, "me", 999L)));

        CurrentRankingResponse response = service.getCurrentRanking(me, null);

        assertThat(response.items()).extracting(RankingItemResponse::userId)
                .containsExactly(lexicographicallyLast, lexicographicallyMiddle, me);
        assertThat(response.items()).extracting(RankingItemResponse::score)
                .containsExactly(100L, 100L, 100L);
        assertThat(response.myRank().rank()).isEqualTo(3L);
        assertThat(response.myRank().scoreGapToFirst()).isZero();
    }

    @Test
    void fallsBackWhenRedisTopMemberIsNotActiveInPostgreSql() {
        assertRedisTopMemberMissingFromActiveDbParticipantsFallsBack();
    }

    @Test
    void fallsBackWhenRedisTopMemberIsWithdrawnInPostgreSql() {
        assertRedisTopMemberMissingFromActiveDbParticipantsFallsBack();
    }

    @Test
    void fallsBackWhenRedisTopMemberIsSuspendedInPostgreSql() {
        assertRedisTopMemberMissingFromActiveDbParticipantsFallsBack();
    }

    @Test
    void fallsBackWhenRedisTopMemberAppUserIsMissingInPostgreSql() {
        assertRedisTopMemberMissingFromActiveDbParticipantsFallsBack();
    }

    private void assertRedisTopMemberMissingFromActiveDbParticipantsFallsBack() {
        UUID me = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID inactive = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID active = UUID.fromString("77777777-7777-7777-7777-777777777777");
        RankingSeason season = activeSeason();
        when(seasonService.getActiveWeeklySeason()).thenReturn(season);
        when(redisRepository.findReadyMeta(1L, 1, properties.maxStaleness(), clock.instant()))
                .thenReturn(Optional.of(readyMeta(2L)));
        when(redisRepository.isGlobalZSetMissing(1L)).thenReturn(false);
        when(redisRepository.findParticipantCount(1L)).thenReturn(2L);
        when(redisRepository.findTopMembers(1L, 50)).thenReturn(List.of(
                new RankingRedisRepository.RankingRedisMember(inactive, 200L, 1L),
                new RankingRedisRepository.RankingRedisMember(active, 100L, 2L)
        ));
        when(entryRepository.findParticipantsByUserIds(season, List.of(inactive, active)))
                .thenReturn(List.of(row(active, "active", 100L)));
        when(entryRepository.findTopParticipants(season, 50)).thenReturn(List.of(row(active, "active", 100L)));
        when(entryRepository.findMyParticipant(season, me)).thenReturn(Optional.empty());
        when(entryRepository.countParticipants(season)).thenReturn(1L);

        CurrentRankingResponse response = service.getCurrentRanking(me, null);

        assertThat(response.items()).extracting(RankingItemResponse::userId).containsExactly(active);
    }

    @Test
    void fallsBackWhenRedisMyRankUserIsNotActiveInPostgreSql() {
        UUID me = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID active = UUID.fromString("77777777-7777-7777-7777-777777777777");
        RankingSeason season = activeSeason();
        when(seasonService.getActiveWeeklySeason()).thenReturn(season);
        when(redisRepository.findReadyMeta(1L, 1, properties.maxStaleness(), clock.instant()))
                .thenReturn(Optional.of(readyMeta(2L)));
        when(redisRepository.isGlobalZSetMissing(1L)).thenReturn(false);
        when(redisRepository.findParticipantCount(1L)).thenReturn(2L);
        when(redisRepository.findTopMembers(1L, 50)).thenReturn(List.of(
                new RankingRedisRepository.RankingRedisMember(active, 100L, 1L)
        ));
        when(entryRepository.findParticipantsByUserIds(season, List.of(active)))
                .thenReturn(List.of(row(active, "active", 100L)));
        when(redisRepository.findRank(1L, me)).thenReturn(2L);
        when(entryRepository.findMyParticipant(season, me)).thenReturn(Optional.empty());
        when(entryRepository.findTopParticipants(season, 50)).thenReturn(List.of(row(active, "active", 100L)));
        when(entryRepository.countParticipants(season)).thenReturn(1L);

        CurrentRankingResponse response = service.getCurrentRanking(me, null);

        assertThat(response.myRank().rank()).isNull();
    }

    @Test
    void fallsBackToPostgreSqlWhenRedisParticipantCountDiffersFromMeta() {
        UUID me = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID first = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        RankingSeason season = activeSeason();
        RankingRedisMeta meta = new RankingRedisMeta(
                RankingRedisMeta.STATE_READY,
                Instant.parse("2026-07-19T00:59:00Z"),
                null,
                2,
                1,
                null,
                Instant.parse("2026-07-19T00:58:00Z"),
                10L
        );
        when(seasonService.getActiveWeeklySeason()).thenReturn(season);
        when(redisRepository.findReadyMeta(1L, 1, properties.maxStaleness(), clock.instant()))
                .thenReturn(Optional.of(meta));
        when(redisRepository.isGlobalZSetMissing(1L)).thenReturn(false);
        when(redisRepository.findParticipantCount(1L)).thenReturn(1L);
        when(entryRepository.findTopParticipants(season, 50)).thenReturn(List.of(row(first, "first", 200L)));
        when(entryRepository.findMyParticipant(season, me)).thenReturn(Optional.empty());
        when(entryRepository.countParticipants(season)).thenReturn(1L);

        CurrentRankingResponse response = service.getCurrentRanking(me, null);

        assertThat(response.items()).extracting(RankingItemResponse::userId).containsExactly(first);
        verify(redisRepository, never()).findTopMembers(1L, 50);
    }

    private RankingEntryRepository.RankingParticipantRow row(UUID userId, String nickname, long score) {
        return new RankingEntryRepository.RankingParticipantRow(userId, nickname, null, score);
    }

    private RankingSeason activeSeason() {
        RankingSeason season = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        ReflectionTestUtils.setField(season, "id", 1L);
        return season;
    }

    private RankingSeason previousSeason() {
        RankingSeason season = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 13),
                Instant.parse("2026-07-12T15:00:00Z"),
                Instant.parse("2026-07-19T15:00:00Z")
        );
        season.startFinalizing();
        season.close(Instant.parse("2026-07-19T15:10:00Z"));
        ReflectionTestUtils.setField(season, "id", 2L);
        return season;
    }

    private RankingRedisMeta readyMeta(long participantCount) {
        return new RankingRedisMeta(
                RankingRedisMeta.STATE_READY,
                Instant.parse("2026-07-19T00:59:00Z"),
                null,
                participantCount,
                1,
                null,
                Instant.parse("2026-07-19T00:58:00Z"),
                10L
        );
    }
}

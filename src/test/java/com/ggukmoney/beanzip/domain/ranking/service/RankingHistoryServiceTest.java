package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingHistoryResponse;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingRewardRepository;
import com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingReward;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.MyWeeklyRankingRewardResponse.RewardStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingHistoryServiceTest {

    private final RankingEntryRepository entryRepository = mock(RankingEntryRepository.class);
    private final RankingHistoryCursorCodec cursorCodec = new RankingHistoryCursorCodec();
    private final WeeklyRankingRewardRepository rewardRepository = mock(WeeklyRankingRewardRepository.class);
    private final RankingHistoryService service = new RankingHistoryService(
            entryRepository,
            cursorCodec,
            rewardRepository,
            Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void usesDefaultSizeAndReturnsNextCursorFromLastReturnedItem() {
        UUID userId = UUID.randomUUID();
        List<RankingEntryRepository.RankingHistoryRow> rows = java.util.stream.LongStream.rangeClosed(1, 21)
                .mapToObj(index -> row(index, Instant.parse("2026-07-26T15:00:00Z").minusSeconds(index), index))
                .toList();
        when(entryRepository.findWeeklyHistory(userId, null, null, 21)).thenReturn(rows);

        RankingHistoryResponse response = service.getHistory(userId, null, null);

        assertThat(response.content()).hasSize(20);
        assertThat(response.hasNext()).isTrue();
        RankingHistoryCursorCodec.Cursor cursor = cursorCodec.decode(response.nextCursor());
        assertThat(cursor.endsAt()).isEqualTo(response.content().get(19).endsAt());
        assertThat(cursor.seasonId()).isEqualTo(20L);
    }

    @Test
    void acceptsSizeOneHundredAndReturnsEmptyPageWithoutCursor() {
        UUID userId = UUID.randomUUID();
        when(entryRepository.findWeeklyHistory(userId, null, null, 101)).thenReturn(List.of());

        RankingHistoryResponse response = service.getHistory(userId, " ", 100);

        assertThat(response.content()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void rejectsInvalidSize() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getHistory(userId, null, 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMMON_VALIDATION_ERROR");
        assertThatThrownBy(() -> service.getHistory(userId, null, 101))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMMON_VALIDATION_ERROR");
    }

    @Test
    void decodesCursorAndPassesCursorValuesToRepository() {
        UUID userId = UUID.randomUUID();
        Instant cursorEndsAt = Instant.parse("2026-07-26T15:00:00Z");
        String cursor = cursorCodec.encode(cursorEndsAt, 12L);

        service.getHistory(userId, cursor, 20);

        verify(entryRepository).findWeeklyHistory(userId, cursorEndsAt, 12L, 21);
    }

    @Test
    void returnsSecondPageFromDecodedCursor() {
        UUID userId = UUID.randomUUID();
        Instant cursorEndsAt = Instant.parse("2026-07-26T15:00:00Z");
        String cursor = cursorCodec.encode(cursorEndsAt, 12L);
        when(entryRepository.findWeeklyHistory(userId, cursorEndsAt, 12L, 3))
                .thenReturn(List.of(row(11L, Instant.parse("2026-07-19T15:00:00Z"), 7L)));

        RankingHistoryResponse response = service.getHistory(userId, cursor, 2);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).seasonCode()).isEqualTo("WEEKLY_11");
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void includesAllRewardStatesAndClaimedOnlyTotal() {
        UUID userId = UUID.randomUUID();
        List<RankingEntryRepository.RankingHistoryRow> rows = List.of(
                row(11L, Instant.parse("2026-09-20T15:00:00Z"), 1L),
                row(10L, Instant.parse("2026-09-13T15:00:00Z"), 2L),
                row(9L, Instant.parse("2026-09-06T15:00:00Z"), 3L),
                row(8L, Instant.parse("2026-08-30T15:00:00Z"), 4L)
        );
        WeeklyRankingReward pending = reward(11L, WeeklyRankingReward.Status.PENDING, false, 10_000L);
        WeeklyRankingReward claimed = reward(10L, WeeklyRankingReward.Status.CLAIMED, false, 5_000L);
        WeeklyRankingReward expired = reward(9L, WeeklyRankingReward.Status.EXPIRED, true, 2_500L);
        when(entryRepository.findWeeklyHistory(userId, null, null, 21)).thenReturn(rows);
        when(rewardRepository.findByUserIdAndSeasonIdIn(userId, List.of(11L, 10L, 9L, 8L)))
                .thenReturn(List.of(pending, claimed, expired));
        when(rewardRepository.sumClaimedPointAmountByUserId(userId)).thenReturn(15_000L);

        RankingHistoryResponse response = service.getHistory(userId, null, null);

        assertThat(response.content()).extracting(item -> item.rewardStatus())
                .containsExactly(RewardStatus.PENDING, RewardStatus.CLAIMED, RewardStatus.EXPIRED, RewardStatus.NONE);
        assertThat(response.content().getFirst().rewardPointAmount()).isEqualTo(10_000L);
        assertThat(response.claimedRewardPointTotal()).isEqualTo(15_000L);
    }

    private WeeklyRankingReward reward(
            long seasonId,
            WeeklyRankingReward.Status status,
            boolean expired,
            long pointAmount
    ) {
        WeeklyRankingReward reward = mock(WeeklyRankingReward.class);
        RankingSeason season = mock(RankingSeason.class);
        when(season.getId()).thenReturn(seasonId);
        when(reward.getSeason()).thenReturn(season);
        when(reward.getStatus()).thenReturn(status);
        when(reward.isExpired(Instant.parse("2026-09-21T00:00:00Z"))).thenReturn(expired);
        when(reward.getPointAmount()).thenReturn(pointAmount);
        return reward;
    }

    private RankingEntryRepository.RankingHistoryRow row(long seasonId, Instant endsAt, long finalRank) {
        return new RankingEntryRepository.RankingHistoryRow(
                seasonId,
                "WEEKLY_" + seasonId,
                endsAt.minusSeconds(604800),
                endsAt,
                finalRank,
                finalRank * 100
        );
    }
}

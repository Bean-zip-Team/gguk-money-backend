package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyRankingRewardPreviewServiceTest {

    private final WeeklyRankingRewardPolicy policy = mock(WeeklyRankingRewardPolicy.class);
    private final RankingEntryRepository entries = mock(RankingEntryRepository.class);
    private final WeeklyRankingRewardPreviewService service = new WeeklyRankingRewardPreviewService(policy, entries);

    @Test
    void previewsActualTopRanksWithoutInternalAccountExclusions() {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        RankingSeason season = mock(RankingSeason.class);
        when(season.getId()).thenReturn(10L);
        when(policy.load(now)).thenReturn(Optional.of(new WeeklyRankingRewardPolicy.Snapshot(
                true, new TreeMap<>(Map.of(1, 10_000L, 2, 5_000L, 3, 2_500L))
        )));
        UUID internalUserId = UUID.randomUUID();
        UUID realUserId = UUID.randomUUID();
        List<RankingEntryRepository.RankingCurrentRewardCandidateRow> candidates = List.of(
                new RankingEntryRepository.RankingCurrentRewardCandidateRow(internalUserId, 900L, 1L),
                new RankingEntryRepository.RankingCurrentRewardCandidateRow(realUserId, 800L, 2L)
        );
        when(entries.findCurrentRewardCandidates(10L, Set.of(), 3)).thenReturn(candidates);

        WeeklyRankingRewardPreviewService.Preview preview = service.preview(
                season, internalUserId, 900L, now);

        assertThat(preview.rewardsByUser().get(internalUserId))
                .isEqualTo(new WeeklyRankingRewardPreviewService.ProvisionalReward(1, 10_000L));
        assertThat(preview.scoreGapToReward()).isZero();
        verify(entries).findCurrentRewardCandidates(10L, Set.of(), 3);
    }
}

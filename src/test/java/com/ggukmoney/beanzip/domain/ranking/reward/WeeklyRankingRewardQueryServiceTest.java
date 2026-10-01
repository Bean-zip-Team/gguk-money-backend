package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingType;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.LatestWeeklyRankingRewardResponse;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyRankingRewardQueryServiceTest {

    private final RankingSeasonRepository seasons = mock(RankingSeasonRepository.class);
    private final WeeklyRankingRewardRepository rewards = mock(WeeklyRankingRewardRepository.class);
    private final Instant now = Instant.parse("2026-09-21T00:00:00Z");
    private final WeeklyRankingRewardQueryService service = new WeeklyRankingRewardQueryService(
            seasons, rewards, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void latestMarksAnUnviewedWinnerAsUnread() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = closedSeason();
        WeeklyRankingReward reward = mock(WeeklyRankingReward.class);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(reward.getUser()).thenReturn(user);
        when(reward.getStatus()).thenReturn(WeeklyRankingReward.Status.PENDING);
        when(reward.isExpired(now)).thenReturn(false);
        when(reward.getViewedAt()).thenReturn(null);
        when(reward.getRewardRank()).thenReturn(1);
        when(reward.getSourceFinalRank()).thenReturn(2L);
        when(reward.getPointAmount()).thenReturn(10_000L);
        when(seasons.findFirstByRankingTypeAndStatusOrderByEndsAtDesc(
                RankingType.WEEKLY, RankingSeasonStatus.CLOSED)).thenReturn(Optional.of(season));
        when(rewards.findAllWithUserBySeasonIdOrderByRewardRank(10L)).thenReturn(List.of(reward));

        LatestWeeklyRankingRewardResponse response = service.latest(userId);

        assertThat(response.hasUnreadWinner()).isTrue();
        assertThat(response.myReward().claimStatus())
                .isEqualTo(com.ggukmoney.beanzip.domain.ranking.reward.dto.MyWeeklyRankingRewardResponse.RewardStatus.PENDING);
    }

    @Test
    void viewUsesOwnedRewardLockAndRecordsTheFirstView() {
        UUID userId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        WeeklyRankingReward reward = mock(WeeklyRankingReward.class);
        when(rewards.findOwnedByPublicIdForUpdate(rewardId, userId)).thenReturn(Optional.of(reward));

        service.markViewed(userId, rewardId);

        verify(reward).markViewed(now);
    }

    private RankingSeason closedSeason() {
        RankingSeason season = mock(RankingSeason.class);
        when(season.getId()).thenReturn(10L);
        when(season.getCode()).thenReturn("WEEKLY_20260914");
        when(season.getStartsAt()).thenReturn(Instant.parse("2026-09-13T15:00:00Z"));
        when(season.getEndsAt()).thenReturn(Instant.parse("2026-09-20T15:00:00Z"));
        return season;
    }
}

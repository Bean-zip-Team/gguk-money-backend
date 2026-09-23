package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WeeklyRankingRewardTest {

    @Test
    void pendingRewardExpiresAtTheDeadlineAndCannotBeExpiredTwice() {
        Instant expiresAt = Instant.parse("2026-09-24T00:00:00Z");
        WeeklyRankingReward reward = WeeklyRankingReward.open(
                mock(RankingSeason.class), mock(AppUser.class), 1L, 1, 100L, 10_000L, expiresAt);

        assertThat(reward.getStatus()).isEqualTo(WeeklyRankingReward.Status.PENDING);
        assertThat(reward.expire(expiresAt)).isTrue();
        assertThat(reward.getStatus()).isEqualTo(WeeklyRankingReward.Status.EXPIRED);
        assertThat(reward.expire(expiresAt.plusSeconds(300))).isFalse();
    }

    @Test
    void viewingARewardIsIdempotent() {
        WeeklyRankingReward reward = WeeklyRankingReward.open(
                mock(RankingSeason.class), mock(AppUser.class), 1L, 1, 100L, 10_000L,
                Instant.parse("2026-09-24T00:00:00Z"));
        Instant firstViewedAt = Instant.parse("2026-09-21T01:00:00Z");

        reward.markViewed(firstViewedAt);
        reward.markViewed(firstViewedAt.plusSeconds(60));

        assertThat(reward.getViewedAt()).isEqualTo(firstViewedAt);
    }
}
